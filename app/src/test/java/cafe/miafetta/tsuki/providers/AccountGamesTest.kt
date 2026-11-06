package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.writeAtomic
import java.io.File
import javax.crypto.KeyGenerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Accounts that hold roles of more than one Skland game, and account files written before games were stored.
class AccountGamesTest {
    @get:Rule val directory = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = CredentialCipher { key }
    private val cred = "test-credential-0001"
    private val file get() = File(directory.root, "accounts.bin")
    private fun repository() = FileAccountRepository(file, cipher)
    private val arknights = SklandRole("000000001", "测试博士 · 官服 · 000000001")
    private val endfield = SklandRole("1~000000001", "测试管理员", SklandGame.ENDFIELD, "官服")

    @Test fun rolesOfBothGamesRoundTripWithTheirGameAndServer() {
        repository().save(SklandAccount("account-a", "", cred, listOf(arknights, endfield)))
        assertEquals(listOf(arknights, endfield), repository().list().single().roles)
    }

    @Test fun accountFilesWrittenBeforeGamesWereStoredReadAsArknights() {
        val legacyRole = JSONObject().put("uid", "000000001").put("label", "测试博士 · 官服 · 000000001")
        val legacy = JSONObject().put("version", 1).put("accounts", JSONArray().put(JSONObject().put("id", "account-a")
            .put("name", "旧账号").put("cred", cred).put("roles", JSONArray().put(legacyRole)).put("retryAt", 0)))
        writeAtomic(file, cipher.encrypt(legacy.toString().toByteArray(Charsets.UTF_8)))
        val role = repository().list().single().roles.single()
        assertEquals(SklandGame.ARKNIGHTS, role.game)
        assertNull(role.server)
    }

    @Test fun equalRoleNumbersOfDifferentGamesAreDifferentRoles() {
        // Endfield role "1~000000001" and Arknights role "000000001" share digits but not identity.
        val stored = SklandAccount("account-a", "", "first-credential-01", listOf(arknights))
        assertNull(matchingAccountForLogin(listOf(stored), "other-credential-01", listOf(endfield)))
        assertSame(stored, matchingAccountForLogin(listOf(stored), "renewed-credential", listOf(arknights, endfield)))
        assertThrows(IllegalArgumentException::class.java) {
            SklandAccount("account-a", "", cred, listOf(endfield, endfield.copy(label = "重复")))
        }
    }

    @Test fun onlyArknightsRolesAreListedByTheArknightsAdapter() {
        val store = repository()
        store.save(SklandAccount("account-a", "别名", cred, listOf(arknights, endfield)))
        val adapter = SklandArknightsAdapter(store, SklandClient(SklandTransport { _, _ -> error("No request expected") }))
        assertEquals(listOf("000000001"), adapter.characters.map { it.characterId })
        val failure = assertThrows(cafe.miafetta.tsuki.core.DataSourceFailure::class.java) {
            adapter.fetchRole("account-a", "1~000000001")
        }
        assertEquals(cafe.miafetta.tsuki.core.RefreshFailure.AUTH_EXPIRED, failure.reason)
    }
}
