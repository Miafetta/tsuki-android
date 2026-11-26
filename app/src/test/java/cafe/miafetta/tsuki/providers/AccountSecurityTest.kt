package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.*
import java.io.File
import javax.crypto.KeyGenerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountSecurityTest {
    @Test fun renewedCredentialMatchesVerifiedRolesAndKeepsAccountIdentity() {
        val old = SklandAccount("account-a", "test", "old-credential", listOf(SklandRole("1", "test")))
        assertSame(old, matchingAccountForLogin(listOf(old), "new-credential", listOf(SklandRole("1", "test"), SklandRole("2", "test"))))
        assertSame(old, matchingAccountForLogin(listOf(old), "old-credential", old.roles))
        assertNull(matchingAccountForLogin(listOf(old), "other-credential", listOf(SklandRole("3", "test"))))
        val store = repository()
        store.save(old)
        val existing = matchingAccountForLogin(store.list(), "new-credential", old.roles)!!
        store.save(SklandAccount(existing.id, "renewed", "new-credential", old.roles))
        val reopened = repository().list().single()
        assertEquals(old.id, reopened.id)
        assertEquals("new-credential", reopened.cred)
    }

    @Test fun accountNameIsAnOptionalAliasStoredAsGiven() {
        val unnamed = SklandAccount("account-a", "", cred, listOf(SklandRole("000000001", "测试博士")))
        repository().save(unnamed)
        assertEquals("", repository().list().single().name)
        repository().save(SklandAccount("account-a", "别名", cred, unnamed.roles))
        assertEquals("别名", repository().list().single().name)
        assertThrows(IllegalArgumentException::class.java) { SklandAccount("account-b", "长".repeat(81), cred, unnamed.roles) }
    }

    @Test fun updatingAnAccountAcceptsOnlyACredOfThatAccount() {
        val first = SklandAccount("account-a", "first", "first-credential", listOf(SklandRole("1", "test")))
        val second = SklandAccount("account-b", "second", "second-credential", listOf(SklandRole("2", "test")))
        val accounts = listOf(first, second)
        assertSame(first, matchingAccountForLogin(accounts, "renewed-credential", first.roles, first.id))
        assertSame(first, matchingAccountForLogin(accounts, first.cred, first.roles, first.id))
        fun problem(cred: String, roles: List<SklandRole>, updatingId: String) =
            assertThrows(AccountProblemException::class.java) { matchingAccountForLogin(accounts, cred, roles, updatingId) }.problem
        // A Skland account that is not stored is not added under the updated account's name...
        assertEquals(AccountProblem.ACCOUNT_MISMATCH, problem("other-credential", listOf(SklandRole("3", "test")), first.id))
        // ...and another stored account is not renamed to it.
        assertEquals(AccountProblem.ACCOUNT_MISMATCH, problem("renewed-credential", second.roles, first.id))
        assertEquals(AccountProblem.ACCOUNT_GONE, problem("renewed-credential", first.roles, "account-removed"))
        // Adding (no account to update) still adds a Skland account that is not stored.
        assertNull(matchingAccountForLogin(accounts, "other-credential", listOf(SklandRole("3", "test"))))
    }

    @Test fun accountRulesNameTheirProblem() {
        val first = SklandAccount("account-a", "test", "first-credential", listOf(SklandRole("1", "test")))
        val second = SklandAccount("account-b", "test", "second-credential", listOf(SklandRole("2", "test")))
        assertEquals(AccountProblem.ACCOUNT_CONFLICT, assertThrows(AccountProblemException::class.java) {
            matchingAccountForLogin(listOf(first, second), "new-credential", first.roles + second.roles)
        }.problem)
        assertEquals(AccountProblem.ACCOUNT_MISMATCH, assertThrows(AccountProblemException::class.java) {
            matchingAccountForLogin(listOf(first), first.cred, listOf(SklandRole("3", "test")))
        }.problem)
        val store = repository()
        repeat(FileAccountRepository.MAX_ACCOUNTS) { store.save(account("account-$it").withRole("$it")) }
        assertEquals(AccountProblem.ACCOUNT_LIMIT, assertThrows(AccountProblemException::class.java) {
            store.save(account("account-extra").withRole("99"))
        }.problem)
        assertEquals(FileAccountRepository.MAX_ACCOUNTS, store.list().size)
    }

    // Reads after a write are served without the Keystore; a file replaced behind the repository is read again.
    @Test fun repeatedReadsDoNotDecryptAgainUntilTheFileChanges() {
        var keyUses = 0
        val counting = CredentialCipher {
            keyUses++
            key
        }
        val store = FileAccountRepository(file, counting)
        store.save(account())
        val afterSave = keyUses
        repeat(3) { assertEquals(listOf("account-1"), store.list().map { it.id }) }
        assertEquals(afterSave, keyUses)
        repository().save(account("account-2-with-a-longer-id"))
        assertEquals(listOf("account-1", "account-2-with-a-longer-id"), store.list().map { it.id })
        assertEquals(afterSave + 1, keyUses)
        store.clear()
        assertTrue(store.list().isEmpty())
        repository().save(account("account-3"))
        assertEquals(listOf("account-3"), store.list().map { it.id })
    }

    private fun SklandAccount.withRole(uid: String) = SklandAccount(id, name, cred, listOf(SklandRole(uid, "测试博士")), retryAt)

    @Test fun loginRejectsMixedOwnershipAndChangedIdentityWithoutChangingStoredAccounts() {
        val first = SklandAccount("account-a", "test", "first-credential", listOf(SklandRole("1", "test")))
        val second = SklandAccount("account-b", "test", "second-credential", listOf(SklandRole("2", "test")))
        val accounts = listOf(first, second)
        assertThrows(IllegalArgumentException::class.java) {
            matchingAccountForLogin(accounts, "new-credential", first.roles + second.roles)
        }
        assertThrows(IllegalArgumentException::class.java) { matchingAccountForLogin(accounts, first.cred, second.roles) }
        assertThrows(IllegalArgumentException::class.java) {
            matchingAccountForLogin(accounts, first.cred, listOf(SklandRole("3", "test")))
        }
        assertThrows(IllegalArgumentException::class.java) { matchingAccountForLogin(accounts, "new-credential", emptyList()) }
        assertEquals("first-credential", first.cred)
        assertEquals("second-credential", second.cred)
    }
    @get:Rule val directory = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = CredentialCipher { key }
    private val cred = "test-credential-0001"
    private val now = 1_700_000_000_000L
    private val file get() = File(directory.root, "accounts.bin")
    private fun repository() = FileAccountRepository(file, cipher)
    private fun account(id: String = "account-1", retryAt: Long = 0) =
        SklandAccount(id, "测试账号", cred, listOf(SklandRole("000000001", "测试博士")), retryAt)
    private fun config(accountId: String = "account-1") = WidgetInstanceConfig(10, "skland",
        "skland.arknights.status", accountId, "000000001")
    private fun data(name: String) = JSONObject(requireNotNull(javaClass.getResource("/skland/$name-redacted.json")).readText())
    private fun response(data: JSONObject) = SklandResponse(200, JSONObject().put("code", 0).put("data", data).toString())

    @Test fun diskAndObjectDescriptionsDoNotContainCredentialsAndDataReopens() {
        val original = account()
        repository().save(original)
        assertFalse(String(file.readBytes(), Charsets.UTF_8).contains(cred))
        assertFalse(original.toString().contains(cred))
        val reopened = repository().list().single()
        assertEquals(cred, reopened.cred)
        assertEquals(original.roles, reopened.roles)
        assertEquals(original.name, reopened.name)
        val first = file.readBytes()
        repository().save(original)
        assertFalse(first.contentEquals(file.readBytes()))
    }

    @Test fun tamperOrLostKeyFailsClosedWithoutOverwritingTheFile() {
        repository().save(account())
        val bytes = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(bytes)
        assertEquals(RefreshFailure.STORAGE, assertThrows(DataSourceFailure::class.java) { repository().list() }.reason)
        assertThrows(DataSourceFailure::class.java) { repository().save(account("account-2")) }
        assertArrayEquals(bytes, file.readBytes())
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(DataSourceFailure::class.java) { FileAccountRepository(file, CredentialCipher { otherKey }).list() }
    }

    @Test fun deletionIsScopedAndEncryptedRateLimitsSurviveRecreation() {
        repository().save(account(retryAt = now + 120000))
        repository().save(account("account-2"))
        assertEquals(now + 120000, repository().list().single { it.id == "account-1" }.retryAt)
        repository().delete("account-2")
        assertEquals(listOf("account-1"), repository().list().map { it.id })
        repository().delete("account-1")
        assertTrue(repository().list().isEmpty())
        assertFalse(String(file.readBytes(), Charsets.UTF_8).contains(cred))
    }

    @Test fun savedRoleBindingFetchesRealAdapterAndRateGateIsLocalToRole() {
        repository().save(account())
        var requests = 0
        val client = SklandClient(SklandTransport { path, _ ->
            requests++
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key")) else response(data("player"))
        }) { now }
        val adapter = SklandArknightsAdapter(repository(), client) { now }
        assertEquals("account-1", adapter.characters.single().accountId)
        val first = adapter.fetch(config(), now)
        assertEquals(81, first.metrics[0].value)
        assertEquals(first, adapter.fetch(config().copy(widgetId = 20), now))
        assertEquals(2, requests)
        assertEquals(RefreshFailure.AUTH_EXPIRED,
            assertThrows(DataSourceFailure::class.java) { adapter.fetch(config("other-account"), now) }.reason)
        repository().delete("account-1")
        adapter.forget("account-1")
        assertEquals(RefreshFailure.AUTH_EXPIRED,
            assertThrows(DataSourceFailure::class.java) { adapter.fetch(config(), now) }.reason)
    }

    @Test fun serverRetryAfterBlocksEvenAfterAdapterAndClientAreRecreated() {
        repository().save(account())
        val client = SklandClient(SklandTransport { path, _ ->
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key")) else SklandResponse(429, "", "300")
        }) { now }
        assertThrows(DataSourceFailure::class.java) { SklandArknightsAdapter(repository(), client) { now }.fetch(config(), now) }
        assertEquals(now + 300000, repository().list().single().retryAt)
        val noRequest = SklandClient(SklandTransport { _, _ ->
            fail("Request should be blocked")
            SklandResponse(500, "")
        }) { now }
        assertEquals(now + 300000, assertThrows(DataSourceFailure::class.java) {
            SklandArknightsAdapter(repository(), noRequest) { now }.fetch(config(), now)
        }.retryAt)
    }

    @Test fun logoutDuringRateLimitedRequestCannotRecreateTheCredential() {
        val repository = repository()
        repository.save(account())
        val client = SklandClient(SklandTransport { path, _ ->
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key"))
            else {
                repository.delete("account-1")
                SklandResponse(429, "", "300")
            }
        }) { now }
        assertThrows(DataSourceFailure::class.java) { SklandArknightsAdapter(repository, client) { now }.fetch(config(), now) }
        assertTrue(repository.list().isEmpty())
    }

    @Test fun explicitResetCanRecoverCorruptStorageAndCreateANewAccount() {
        repository().save(account())
        file.writeText("corrupt encrypted file")
        assertThrows(DataSourceFailure::class.java) { repository().list() }
        repository().clear()
        assertFalse(file.exists())
        repository().save(account("re-authorized"))
        assertEquals("re-authorized", repository().list().single().id)
    }

    @Test fun credentialChangeDuringFetchRejectsOldSnapshotAndRateLimit() {
        for (rateLimited in listOf(false, true)) {
            val repository = repository()
            repository.save(account())
            var persisted = false
            val replacement = SklandAccount("account-1", "更新账号", "replacement-credential", account().roles)
            val client = SklandClient(SklandTransport { path, _ ->
                if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key"))
                else {
                    repository.save(replacement)
                    if (rateLimited) SklandResponse(429, "", "300") else response(data("player"))
                }
            }) { now }
            val adapter = SklandArknightsAdapter(repository, client, onSnapshot = { _, _, _ -> persisted = true }) { now }
            val failure = assertThrows(DataSourceFailure::class.java) { adapter.fetch(config(), now) }
            assertEquals(if (rateLimited) RefreshFailure.RATE_LIMIT else RefreshFailure.AUTH_EXPIRED, failure.reason)
            assertFalse(persisted)
            assertEquals(replacement.cred, repository.list().single().cred)
            assertEquals(0L, repository.list().single().retryAt)
        }
    }

    @Test fun unavailableKeyProviderReportsStorageFailureWithoutDestroyingData() {
        repository().save(account())
        val original = file.readBytes()
        val failing = FileAccountRepository(file, CredentialCipher { throw java.security.ProviderException("private failure") })
        assertEquals(RefreshFailure.STORAGE, assertThrows(DataSourceFailure::class.java) { failing.list() }.reason)
        assertThrows(DataSourceFailure::class.java) { failing.save(account("new")) }
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun removedRoleDuringFetchCannotPublishOrRepopulateItsCache() {
        val store = repository()
        store.save(account())
        var persisted = false
        val client = SklandClient(SklandTransport { path, _ ->
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key"))
            else {
                store.save(SklandAccount("account-1", "test", cred, listOf(SklandRole("000000002", "test"))))
                response(data("player"))
            }
        }) { now }
        val adapter = SklandArknightsAdapter(store, client, onSnapshot = { _, _, _ -> persisted = true }) { now }
        val failure = assertThrows(DataSourceFailure::class.java) { adapter.fetch(config(), now) }
        assertEquals(RefreshFailure.AUTH_EXPIRED, failure.reason)
        assertFalse(persisted)
        assertEquals("000000002", store.list().single().roles.single().uid)
    }
}
