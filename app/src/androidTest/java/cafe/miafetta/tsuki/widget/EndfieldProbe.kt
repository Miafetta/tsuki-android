package cafe.miafetta.tsuki.widget

import android.content.Context
import cafe.miafetta.tsuki.providers.AndroidCredentialKey
import cafe.miafetta.tsuki.providers.CredentialCipher
import cafe.miafetta.tsuki.providers.FileAccountRepository
import cafe.miafetta.tsuki.providers.SklandResponse
import cafe.miafetta.tsuki.providers.SklandSigner
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import org.json.JSONObject

// Opt-in probe for Endfield test samples (scenario endfield-probe, DEVELOPMENT_CONSTRAINTS §8). With the Cred
// already stored on this device it requests, once per account, the Skland binding list, the account user ID and each
// Endfield role's card, and writes the response bodies to the app's external files for adb pull. Accounts, caches and
// widgets are not written. The Cred and the session token are never logged or saved, and the result lists only
// HTTP statuses and response codes.
internal fun endfieldProbe(context: Context): String {
    val file = File(context.noBackupFilesDir, "accounts/v1.bin")
    if (!file.exists()) return "SKIP: no stored account"
    val accounts = FileAccountRepository(file, CredentialCipher(AndroidCredentialKey::get)).list()
    val output = File(requireNotNull(context.getExternalFilesDir(null)), "endfield-probe")
    val lines = accounts.flatMapIndexed { index, account -> probeAccount(account.cred, File(output, "account-${index + 1}")) }
    return "PASS: endfield probe, ${accounts.size} account(s)\n" + lines.joinToString("\n")
}

private fun probeAccount(cred: String, folder: File): List<String> {
    check(folder.isDirectory || folder.mkdirs())
    val session = ProbeSession(cred)
    val lines = mutableListOf<String>()
    fun read(name: String, path: String): JSONObject? {
        val response = session.get(path)
        lines += "${folder.name} $name: HTTP ${response.status}, code ${codeOf(response.body)}"
        if (response.body.isNotEmpty()) File(folder, "$name.json").writeText(response.body)
        return runCatching { JSONObject(response.body).optJSONObject("data") }.getOrNull()
    }
    val binding = read("binding", "/api/v1/game/player/binding") ?: return lines
    // /user/teenager carries the user ID under "teenager"; /user/me is only a fallback because it returns the whole profile.
    val userId = read("user-teenager", "/api/v1/user/teenager")?.optJSONObject("teenager")?.optString("userId")
        ?.takeIf { it.isNotEmpty() }
        ?: read("user-me", "/api/v1/user/me")?.optJSONObject("user")?.optString("id")?.takeIf { it.isNotEmpty() }
    if (userId == null) return lines + "${folder.name}: no user ID found, card requests skipped"
    endfieldRoles(binding).forEachIndexed { index, (serverId, roleId) ->
        val query = "roleId=${encode(roleId)}&serverId=${encode(serverId)}&userId=${encode(userId)}"
        read("card-${index + 1}", "/web/v1/game/endfield/card/detail?$query")
    }
    return lines
}

// Server and role IDs of every Endfield role in the raw binding list, read as loosely as possible.
private fun endfieldRoles(binding: JSONObject): List<Pair<String, String>> {
    val apps = binding.optJSONArray("list") ?: return emptyList()
    return (0 until apps.length()).mapNotNull(apps::optJSONObject).filter { it.optString("appCode") == "endfield" }
        .flatMap { app ->
            val bindings = app.optJSONArray("bindingList") ?: return@flatMap emptyList()
            (0 until bindings.length()).mapNotNull(bindings::optJSONObject).flatMap { item ->
                val roles = item.optJSONArray("roles") ?: return@flatMap emptyList()
                (0 until roles.length()).mapNotNull(roles::optJSONObject)
                    .map { it.optString("serverId") to it.optString("roleId") }
                    .filter { (server, role) -> server.isNotEmpty() && role.isNotEmpty() }
            }
        }.take(10)
}

private fun codeOf(body: String) = runCatching { JSONObject(body).opt("code")?.toString() }.getOrNull() ?: "none"
private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private class ProbeSession(private val cred: String) {
    private var token: String? = null

    fun get(path: String): SklandResponse {
        val current = token ?: refresh()
        return request(path, SklandSigner.headers(cred, current, path, System.currentTimeMillis() / 1000 - 1))
    }

    // The refresh body carries the session token, so it is read here and never written anywhere.
    private fun refresh(): String {
        val response = request("/api/v1/auth/refresh", mapOf("cred" to cred))
        val value = runCatching { JSONObject(response.body).getJSONObject("data").getString("token") }.getOrNull()
        check(value != null) { "Session refresh failed: HTTP ${response.status}, code ${codeOf(response.body)}" }
        return value.also { token = it }
    }

    private fun request(pathAndQuery: String, headers: Map<String, String>): SklandResponse {
        require(pathAndQuery.substringBefore('?') in PATHS)
        val connection = URI("https://zonai.skland.com$pathAndQuery").toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Tsuki/${cafe.miafetta.tsuki.BuildConfig.VERSION_NAME} (Android)")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return SklandResponse(status, body)
        } finally { connection.disconnect() }
    }

    private companion object {
        val PATHS = setOf("/api/v1/auth/refresh", "/api/v1/game/player/binding", "/api/v1/user/teenager",
            "/api/v1/user/me", "/web/v1/game/endfield/card/detail")
    }
}
