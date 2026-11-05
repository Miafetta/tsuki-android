package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.writeAtomic
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal object OfficialAvatars {
    fun safe(url: String?): String? = url?.takeIf {
        it.length <= 2048 && runCatching {
            val uri = URI(it)
            uri.scheme == "https" && uri.host == "web.hycdn.cn" && uri.port == -1 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.path.startsWith("/arknights/game/assets/") && !uri.path.contains("..") &&
                !uri.rawPath.contains('%')
        }.getOrDefault(false)
    }
    fun character(id: String): String? = if (id.matches(Regex("char_[A-Za-z0-9_]{1,70}")))
        "https://web.hycdn.cn/arknights/game/assets/char/avatar/$id.png" else null
}

internal class AvatarCache(private val directory: File,
    private val download: (String) -> ByteArray = ::downloadOfficialAvatar) {
    private fun file(url: String) = File(directory,
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } + ".img")

    fun read(url: String?): ByteArray? {
        val safe = OfficialAvatars.safe(url) ?: return null
        return runCatching {
            val file = file(safe)
            if (!file.exists() || file.length() !in 12..MAX_BYTES.toLong()) return null
            file.readBytes().takeIf(::imageHeader)
        }.getOrNull()
    }

    // Returns true only after replacing a cached image, so unchanged avatars do not redraw widgets.
    fun prepare(url: String?): Boolean {
        val safe = OfficialAvatars.safe(url) ?: return false
        if (read(safe) != null && System.currentTimeMillis() - file(safe).lastModified() < 7 * 86400000L) return false
        return runCatching {
            val bytes = download(safe)
            require(bytes.size in 12..MAX_BYTES && imageHeader(bytes))
            writeAtomic(file(safe), bytes)
            directory.listFiles()?.filter { it.extension == "img" }?.sortedByDescending { it.lastModified() }
                ?.drop(32)?.forEach { it.delete() }
            true
        }.getOrDefault(false)
    }

    companion object {
        const val MAX_BYTES = 512 * 1024
        private fun imageHeader(bytes: ByteArray) = bytes.size >= 12 && (
            bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte) ||
            bytes[0] == 255.toByte() && bytes[1] == 216.toByte() && bytes[2] == 255.toByte())
    }
}

private fun downloadOfficialAvatar(url: String): ByteArray {
    require(OfficialAvatars.safe(url) != null)
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.setRequestProperty("User-Agent", "Tsuki/${cafe.miafetta.tsuki.BuildConfig.VERSION_NAME} (Android)")
        // Never reuse the signed game client or copy authentication headers to the CDN.
        require(connection.responseCode == 200 && connection.contentLengthLong <= AvatarCache.MAX_BYTES)
        val deadline = System.nanoTime() + 8_000_000_000L
        return connection.inputStream.use { input ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline)
                    throw java.io.InterruptedIOException()
                val count = input.read(buffer)
                if (count < 0) break
                require(result.size() + count <= AvatarCache.MAX_BYTES)
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        }
    } finally { connection.disconnect() }
}
