package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.AccountProblem
import cafe.miafetta.tsuki.core.AccountProblemException
import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.writeAtomic
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal class SklandAccount(val id: String, val name: String, val cred: String, val roles: List<SklandRole>,
    val retryAt: Long = 0) {
    init {
        // name is the user's optional alias; the UI shows the platform name when it is blank.
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && name.length <= 80)
        SklandClient.validateCred(cred)
        require(roles.isNotEmpty() && roles.size <= SklandRoles.LIMIT && roles.distinctBy { it.key }.size == roles.size &&
            retryAt >= 0)
    }
    override fun toString() = "SklandAccount(redacted)"
}

internal interface AccountRepository {
    fun list(): List<SklandAccount>
    fun save(account: SklandAccount)
    fun delete(id: String)
    fun clear()
}

// [updatingId] is the account "更新 Cred" was opened for. The Cred must then match that very account: one of another
// Skland account would otherwise be added as a new account under the updated account's name, or rename the stored
// account it does match.
internal fun matchingAccountForLogin(accounts: List<SklandAccount>, cred: String, roles: List<SklandRole>,
    updatingId: String? = null): SklandAccount? {
    val keys = roles.map { it.key }.toSet()
    require(keys.isNotEmpty())
    if (updatingId != null && accounts.none { it.id == updatingId }) {
        throw AccountProblemException(AccountProblem.ACCOUNT_GONE, "Account no longer exists")
    }
    val matches = accounts.filter { it.cred == cred || it.roles.any { role -> role.key in keys } }
    if (matches.size > 1) {
        throw AccountProblemException(AccountProblem.ACCOUNT_CONFLICT, "Roles belong to multiple stored accounts")
    }
    val match = matches.singleOrNull()
    if (updatingId != null && match?.id != updatingId) {
        throw AccountProblemException(AccountProblem.ACCOUNT_MISMATCH, "Credential belongs to a different account")
    }
    return match?.also { existing ->
        cafe.miafetta.tsuki.core.validateAccountUpdate(existing.roles.map { it.key }, keys.toList(),
            accounts.filter { it.id != existing.id }.flatMap { it.roles }.map { it.key })
    }
}

internal class CredentialCipher(private val key: () -> SecretKey) {
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size >= 29 && bytes[0].toInt() == 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(AAD)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }

    companion object { private val AAD = "cafe.miafetta.tsuki.accounts.v1".toByteArray(Charsets.UTF_8) }
}

internal class FileAccountRepository(private val file: File, private val cipher: CredentialCipher) : AccountRepository {
    // The accounts as last read or written, with the file's stamp at that time. Each read used to load the Keystore key
    // and decrypt the whole file, and a single widget update read the accounts several times, partly on the main thread.
    // This process is the only writer. The stamp invalidates the cache if the file size or modification time changes.
    private var cached: Pair<FileStamp, List<SklandAccount>>? = null

    private data class FileStamp(val modified: Long, val length: Long)
    private fun stamp() = FileStamp(file.lastModified(), file.length())

    @Synchronized override fun list(): List<SklandAccount> {
        if (!file.exists()) {
            cached = null
            return emptyList()
        }
        val stamp = stamp()
        cached?.takeIf { it.first == stamp }?.let { return it.second }
        return decrypt().also { cached = stamp to it }
    }

    private fun decrypt(): List<SklandAccount> {
        try {
            require(file.length() <= 512 * 1024)
            val plaintext = cipher.decrypt(file.readBytes())
            try {
                val root = JSONObject(String(plaintext, Charsets.UTF_8))
                require(root.getInt("version") == 1)
                val values = root.getJSONArray("accounts")
                require(values.length() <= MAX_ACCOUNTS)
                return (0 until values.length()).map { index ->
                    val value = values.getJSONObject(index)
                    val roles = value.getJSONArray("roles")
                    SklandAccount(value.getString("id"), value.getString("name"), value.getString("cred"),
                        (0 until roles.length()).map { roleIndex -> readRole(roles.getJSONObject(roleIndex)) },
                        value.optLong("retryAt", 0))
                }.also { accounts -> require(accounts.distinctBy { it.id }.size == accounts.size) }
            } finally { plaintext.fill(0) }
        } catch (_: GeneralSecurityException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: java.security.ProviderException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: IllegalArgumentException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: org.json.JSONException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
    }

    @Synchronized override fun save(account: SklandAccount) {
        val accounts = list().filter { it.id != account.id } + account
        if (accounts.size > MAX_ACCOUNTS) throw AccountProblemException(AccountProblem.ACCOUNT_LIMIT, "Account limit reached")
        write(accounts)
    }

    @Synchronized override fun delete(id: String) { write(list().filter { it.id != id }) }

    @Synchronized override fun clear() {
        cached = null
        java.nio.file.Files.deleteIfExists(file.toPath())
    }

    private fun write(accounts: List<SklandAccount>) {
        val values = JSONArray()
        accounts.forEach { account ->
            val roles = JSONArray()
            account.roles.forEach {
                roles.put(JSONObject().put("uid", it.uid).put("label", it.label).put("game", it.game.appCode)
                    .put("server", it.server))
            }
            values.put(JSONObject().put("id", account.id).put("name", account.name).put("cred", account.cred)
                .put("roles", roles).put("retryAt", account.retryAt))
        }
        val plaintext = JSONObject().put("version", 1).put("accounts", values).toString().toByteArray(Charsets.UTF_8)
        cached = null
        try {
            writeAtomic(file, cipher.encrypt(plaintext))
            cached = stamp() to accounts
        } catch (_: GeneralSecurityException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: java.security.ProviderException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        finally { plaintext.fill(0) }
    }

    // Roles saved before 2.2.0 have neither "game" nor "server"; they are all Arknights roles.
    private fun readRole(role: JSONObject) = SklandRole(role.getString("uid"), role.getString("label"),
        requireNotNull(SklandGame.fromAppCode(role.optString("game", SklandGame.ARKNIGHTS.appCode))),
        role.optString("server").takeIf { it.isNotEmpty() })

    companion object {
        // Saving one more account is refused; reading a file with more is a damaged file.
        const val MAX_ACCOUNTS = 10
    }
}
