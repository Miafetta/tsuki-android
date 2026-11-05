package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.RefreshFailure
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

internal data class SklandResponse(val status: Int, val body: String, val retryAfter: String? = null)
internal fun interface SklandTransport {
    fun request(pathAndQuery: String, headers: Map<String, String>): SklandResponse
}

internal class SklandHttpsTransport : SklandTransport {
    override fun request(pathAndQuery: String, headers: Map<String, String>): SklandResponse {
        require(pathAndQuery.substringBefore('?') in setOf("/api/v1/auth/refresh",
            "/api/v1/game/player/binding", "/api/v1/game/player/info"))
        val connection = URI("https://zonai.skland.com$pathAndQuery").toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Tsuki/${cafe.miafetta.tsuki.BuildConfig.VERSION_NAME} (Android)")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val status = connection.responseCode
            if (status !in 200..299) return SklandResponse(status, "", connection.getHeaderField("Retry-After"))
            val body = connection.inputStream.use { input ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 4 * 1024 * 1024) throw DataSourceFailure(RefreshFailure.INVALID_DATA)
                    bytes.write(buffer, 0, count)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
            return SklandResponse(status, body)
        } catch (failure: DataSourceFailure) {
            throw failure
        } catch (_: java.io.IOException) {
            throw DataSourceFailure(RefreshFailure.NETWORK)
        } finally { connection.disconnect() }
    }
}

internal object SklandSigner {
    fun headers(cred: String, token: String, pathAndQuery: String, seconds: Long): Map<String, String> {
        val timestamp = seconds.toString()
        // Signature order and raw query bytes are part of the server protocol.
        val header = "{\"platform\":\"\",\"timestamp\":\"$timestamp\",\"dId\":\"\",\"vName\":\"\"}"
        val path = pathAndQuery.substringBefore('?')
        val query = pathAndQuery.substringAfter('?', "")
        val hmac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }.doFinal((path + query + timestamp + header).toByteArray(Charsets.UTF_8)).hex()
        val sign = MessageDigest.getInstance("MD5").digest(hmac.toByteArray(Charsets.UTF_8)).hex()
        return linkedMapOf("cred" to cred, "platform" to "", "timestamp" to timestamp,
            "dId" to "", "vName" to "", "sign" to sign)
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal data class SklandRole(val uid: String, val label: String) {
    init { require(uid.matches(Regex("[0-9]{1,20}")) && label.isNotBlank() && label.length <= 80) }
}

internal class SklandClient(private val transport: SklandTransport,
    private val clock: () -> Long = System::currentTimeMillis) {
    private data class Session(val token: String, val expires: Long)
    private val sessions = mutableMapOf<String, Session>()
    private val blockedUntil = mutableMapOf<String, Long>()

    @Synchronized fun forget(cred: String) {
        sessions.remove(cred)
        blockedUntil.remove(cred)
    }
    @Synchronized fun clear() {
        sessions.clear()
        blockedUntil.clear()
    }

    @Synchronized fun roles(cred: String): List<SklandRole> = parseRoles(signed(cred, "/api/v1/game/player/binding"))

    @Synchronized fun player(cred: String, uid: String): JSONObject {
        require(uid.matches(Regex("[0-9]{1,20}")))
        return signed(cred, "/api/v1/game/player/info?uid=${URLEncoder.encode(uid, Charsets.UTF_8.name())}")
    }

    private fun signed(cred: String, path: String): JSONObject {
        validateCred(cred)
        val now = clock()
        val retryAt = blockedUntil[cred] ?: 0
        if (now < retryAt) throw DataSourceFailure(RefreshFailure.RATE_LIMIT, retryAt)
        for (attempt in 0..1) {
            val session = sessions[cred]?.takeIf { it.expires > clock() } ?: refresh(cred)
            try {
                return data(transport.request(path, SklandSigner.headers(cred, session.token, path, clock() / 1000 - 1)))
            } catch (failure: DataSourceFailure) {
                if (failure.reason == RefreshFailure.AUTH_EXPIRED && attempt == 0) {
                    sessions.remove(cred)
                    continue
                }
                if (failure.reason == RefreshFailure.RATE_LIMIT) blockedUntil[cred] = failure.retryAt
                throw failure
            }
        }
        throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
    }

    private fun refresh(cred: String): Session {
        val value = try { data(transport.request("/api/v1/auth/refresh", mapOf("cred" to cred))) }
        catch (failure: DataSourceFailure) {
            if (failure.reason == RefreshFailure.RATE_LIMIT) blockedUntil[cred] = failure.retryAt
            throw failure
        }
        val token = try { value.getString("token") } catch (_: org.json.JSONException) {
            throw DataSourceFailure(RefreshFailure.INVALID_DATA)
        }
        if (!token.matches(Regex("[!-~]{1,4096}"))) throw DataSourceFailure(RefreshFailure.INVALID_DATA)
        return Session(token, clock() + 30 * 60_000L).also { sessions[cred] = it }
    }

    private fun data(response: SklandResponse): JSONObject {
        when {
            response.status == 401 || response.status == 403 -> throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
            response.status == 429 -> throw DataSourceFailure(RefreshFailure.RATE_LIMIT, retryAt(response.retryAfter, clock()))
            response.status !in 200..299 -> throw DataSourceFailure(RefreshFailure.SERVER)
        }
        try {
            val root = JSONObject(response.body)
            when (root.getInt("code")) {
                0 -> return root.getJSONObject("data")
                10000, 10002 -> throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
                10003 -> throw DataSourceFailure(RefreshFailure.CLOCK_SKEW)
                429 -> throw DataSourceFailure(RefreshFailure.RATE_LIMIT, clock() + 60_000)
                else -> throw DataSourceFailure(RefreshFailure.SERVER)
            }
        } catch (_: org.json.JSONException) { throw DataSourceFailure(RefreshFailure.INVALID_DATA) }
    }

    companion object {
        fun validateCred(cred: String) { require(cred.matches(Regex("[!-~]{10,4096}"))) { "Invalid credential" } }

        fun parseRoles(data: JSONObject): List<SklandRole> = try {
            val apps = data.getJSONArray("list")
            val roles = mutableListOf<SklandRole>()
            for (index in 0 until apps.length()) {
                val app = apps.getJSONObject(index)
                if (app.getString("appCode") != "arknights") continue
                val bindings = app.getJSONArray("bindingList")
                require(bindings.length() <= 20)
                for (bindingIndex in 0 until bindings.length()) {
                    val role = bindings.getJSONObject(bindingIndex)
                    val uid = role.getString("uid")
                    val name = role.getString("nickName").take(40)
                    val channel = role.optString("channelName", "").take(20)
                    roles.add(SklandRole(uid, "$name · $channel · $uid".take(80)))
                }
            }
            require(roles.isNotEmpty() && roles.size <= 20 && roles.distinctBy { it.uid }.size == roles.size)
            roles
        } catch (_: org.json.JSONException) { throw DataSourceFailure(RefreshFailure.INVALID_DATA) }
        catch (_: IllegalArgumentException) { throw DataSourceFailure(RefreshFailure.INVALID_DATA) }

        fun retryAt(value: String?, now: Long): Long {
            val duration = value?.toLongOrNull()?.takeIf { it >= 0 }?.coerceAtMost(86_400)?.times(1000)
            val date = runCatching { java.time.ZonedDateTime.parse(value,
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            return (duration?.let { now + it } ?: date ?: (now + 60_000)).coerceIn(now + 1000, now + 86_400_000)
        }
    }
}
