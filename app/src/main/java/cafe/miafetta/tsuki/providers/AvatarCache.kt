package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.writeAtomic
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

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

// [background] runs the renewals that a read does not wait for (prepareForRead). [download] stays last, so a trailing
// lambda is still the download.
internal class AvatarCache(private val directory: File,
    private val background: Executor = Executor(Runnable::run),
    private val download: (String) -> ByteArray = ::downloadOfficialAvatar) {
    private val renewing = ConcurrentHashMap.newKeySet<String>()
    private fun file(url: String) = File(directory,
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } + ".img")
    private fun fresh(file: File) = System.currentTimeMillis() - file.lastModified() < MAX_AGE_MILLIS

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
        if (read(safe) != null && fresh(file(safe))) return false
        return runCatching {
            val bytes = download(safe)
            require(bytes.size in 12..MAX_BYTES && imageHeader(bytes))
            writeAtomic(file(safe), bytes)
            directory.listFiles()?.filter { it.extension == "img" }?.sortedByDescending { it.lastModified() }
                ?.drop(32)?.forEach { it.delete() }
            true
        }.getOrDefault(false)
    }

    // For a read that shows the avatar (an overview read, not a refresh): one never cached is downloaded now so that it
    // can be shown, while one that is only due for renewal is shown as it is and renewed on [background], so the read
    // does not wait for the network. One renewal per avatar at a time.
    fun prepareForRead(url: String?) {
        val safe = OfficialAvatars.safe(url) ?: return
        if (read(safe) == null) {
            prepare(safe)
            return
        }
        if (fresh(file(safe)) || !renewing.add(safe)) return
        try {
            background.execute {
                try { prepare(safe) } finally { renewing.remove(safe) }
            }
        } catch (_: RejectedExecutionException) {
            renewing.remove(safe)
        }
    }

    companion object {
        const val MAX_BYTES = 512 * 1024
        // A cached avatar older than this is downloaded again; until then it is shown without a request.
        const val MAX_AGE_MILLIS = 7 * 86_400_000L
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
