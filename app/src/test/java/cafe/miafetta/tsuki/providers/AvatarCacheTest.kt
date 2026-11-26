package cafe.miafetta.tsuki.providers

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AvatarCacheTest {
    @get:Rule val directory = TemporaryFolder()
    private val url = "https://web.hycdn.cn/arknights/game/assets/char/avatar/char_test_trainee.png"
    private fun png() = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0)

    @Test fun onlyVerifiedHttpsOriginAndPathsAreAccepted() {
        assertEquals(url, OfficialAvatars.safe(url))
        assertEquals(url, OfficialAvatars.character("char_test_trainee"))
        for (other in listOf(url.replace("https:", "http:"), url.replace("web.hycdn.cn", "third-party.test"),
            url.replace("web.hycdn.cn", "web.hycdn.cn.evil.test"), url.replace("web.hycdn.cn", "user@web.hycdn.cn"),
            "$url?token=secret", "$url#fragment", url.replace("/char/avatar/", "/../"),
            url.replace("/char/avatar/", "/%2e%2e/"), "https://web.hycdn.cn/unverified.png")) {
            assertNull(OfficialAvatars.safe(other))
        }
        assertNull(OfficialAvatars.character("../trainer"))
    }

    @Test fun readNeverDownloadsAndFailuresReturnPlaceholderWithoutCreatingFiles() {
        var calls = 0
        val cache = AvatarCache(directory.root) {
            calls++
            throw java.io.IOException()
        }
        assertNull(cache.read(url))
        assertEquals(0, calls)
        assertFalse(cache.prepare(url))
        assertNull(cache.read(url))
        assertEquals(1, calls)
        assertEquals(0, directory.root.listFiles()!!.size)
        assertFalse(cache.prepare("https://third-party.test/avatar.png"))
        assertEquals(1, calls)
    }

    @Test fun validImagesAreCachedAndOversizedOrNonImagesAreRejected() {
        var calls = 0
        val cache = AvatarCache(directory.root) {
            calls++
            png()
        }
        assertTrue(cache.prepare(url))
        assertFalse(cache.prepare(url))
        assertArrayEquals(png(), cache.read(url))
        assertEquals(1, calls)
        assertTrue(directory.root.listFiles()!!.single().name.matches(Regex("[0-9a-f]{64}\\.img")))
        val oversized = png().copyOf(AvatarCache.MAX_BYTES + 1)
        assertFalse(AvatarCache(directory.root) { oversized }.prepare(url.replace("trainee", "oversized")))
        assertFalse(AvatarCache(directory.root) { ByteArray(100) }.prepare(url.replace("trainee", "invalid")))
        directory.root.listFiles()!!.single().writeBytes(ByteArray(100))
        assertNull(cache.read(url))
    }

    @Test fun staleAvatarDownloadFailureKeepsLastImageAndDoesNotRequestRedraw() {
        val cache = AvatarCache(directory.root) { png() }
        assertTrue(cache.prepare(url))
        val file = directory.root.listFiles()!!.single()
        assertTrue(file.setLastModified(System.currentTimeMillis() - 8 * 86400000L))
        assertFalse(AvatarCache(directory.root) { throw java.io.IOException() }.prepare(url))
        assertArrayEquals(png(), cache.read(url))
        assertTrue(cache.prepare(url))
        assertFalse(cache.prepare(url))
    }

    // An overview read waits only for an avatar it has never cached; renewing a stale one is handed to the background.
    @Test fun readsDownloadMissingAvatarsAtOnceAndRenewStaleOnesInTheBackground() {
        var calls = 0
        val queued = mutableListOf<Runnable>()
        val cache = AvatarCache(directory.root, background = { queued += it }) {
            calls++
            png()
        }
        cache.prepareForRead(url)
        assertEquals(1, calls)
        assertTrue(queued.isEmpty())
        cache.prepareForRead(url)
        assertEquals(1, calls)
        assertTrue(queued.isEmpty())
        assertTrue(directory.root.listFiles()!!.single().setLastModified(System.currentTimeMillis() - 8 * 86400000L))
        cache.prepareForRead(url)
        cache.prepareForRead(url)
        assertEquals(1, calls)
        assertEquals(1, queued.size)
        queued.single().run()
        assertEquals(2, calls)
        assertArrayEquals(png(), cache.read(url))
        cache.prepareForRead("https://third-party.test/avatar.png")
        assertEquals(2, calls)
    }
}
