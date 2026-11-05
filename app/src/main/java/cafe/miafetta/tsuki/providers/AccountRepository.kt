package cafe.miafetta.tsuki.providers

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
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && name.isNotBlank() && name.length <= 80)
        SklandClient.validateCred(cred)
        require(roles.isNotEmpty() && roles.size <= 20 && roles.distinctBy { it.uid }.size == roles.size && retryAt >= 0)
    }
    override fun toString() = "SklandAccount(redacted)"
}

internal interface AccountRepository {
    fun list(): List<SklandAccount>
    fun save(account: SklandAccount)
    fun delete(id: String)
    fun clear()
}

internal fun matchingAccountForLogin(accounts: List<SklandAccount>, cred: String, roles: List<SklandRole>): SklandAccount? {
    val ids = roles.map { it.uid }.toSet()
    require(ids.isNotEmpty())
    val matches = accounts.filter { it.cred == cred || it.roles.any { role -> role.uid in ids } }
    require(matches.size <= 1) { "Roles belong to multiple stored accounts" }
    return matches.singleOrNull()?.also { existing ->
        cafe.miafetta.tsuki.core.validateAccountUpdate(existing.roles.map { it.uid }, ids.toList(),
            accounts.filter { it.id != existing.id }.flatMap { it.roles }.map { it.uid })
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
    @Synchronized override fun list(): List<SklandAccount> {
        if (!file.exists()) return emptyList()
        try {
            require(file.length() <= 512 * 1024)
            val plaintext = cipher.decrypt(file.readBytes())
            try {
                val root = JSONObject(String(plaintext, Charsets.UTF_8))
                require(root.getInt("version") == 1)
                val values = root.getJSONArray("accounts")
                require(values.length() <= 10)
                return (0 until values.length()).map { index ->
                    val value = values.getJSONObject(index)
                    val roles = value.getJSONArray("roles")
                    SklandAccount(value.getString("id"), value.getString("name"), value.getString("cred"),
                        (0 until roles.length()).map { roleIndex ->
                            val role = roles.getJSONObject(roleIndex)
                            SklandRole(role.getString("uid"), role.getString("label"))
                        }, value.optLong("retryAt", 0))
                }.also { accounts -> require(accounts.distinctBy { it.id }.size == accounts.size) }
            } finally { plaintext.fill(0) }
        } catch (_: GeneralSecurityException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: java.security.ProviderException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: IllegalArgumentException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: org.json.JSONException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
    }

    @Synchronized override fun save(account: SklandAccount) {
        val accounts = list().filter { it.id != account.id } + account
        require(accounts.size <= 10) { "Account limit reached" }
        write(accounts)
    }

    @Synchronized override fun delete(id: String) { write(list().filter { it.id != id }) }

    @Synchronized override fun clear() { java.nio.file.Files.deleteIfExists(file.toPath()) }

    private fun write(accounts: List<SklandAccount>) {
        val values = JSONArray()
        accounts.forEach { account ->
            val roles = JSONArray()
            account.roles.forEach { roles.put(JSONObject().put("uid", it.uid).put("label", it.label)) }
            values.put(JSONObject().put("id", account.id).put("name", account.name).put("cred", account.cred)
                .put("roles", roles).put("retryAt", account.retryAt))
        }
        val plaintext = JSONObject().put("version", 1).put("accounts", values).toString().toByteArray(Charsets.UTF_8)
        try { writeAtomic(file, cipher.encrypt(plaintext)) }
        catch (_: GeneralSecurityException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        catch (_: java.security.ProviderException) { throw DataSourceFailure(RefreshFailure.STORAGE) }
        finally { plaintext.fill(0) }
    }
}
