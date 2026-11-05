package cafe.miafetta.tsuki.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarFrameTest {
    private fun image(size: Int, color: (Int, Int) -> Int) = IntArray(size * size) { color(it % size, it / size) }
    private fun gray(value: Int, alpha: Int = 255) = (alpha shl 24) or (value shl 16) or (value shl 8) or value

    @Test fun trimsABakedDarkFrame() {
        // Mirrors the official player avatar: about 5% of the edge darkens from the content towards the border.
        val pixels = image(150) { x, y ->
            val edge = minOf(x, y, 149 - x, 149 - y)
            gray(if (edge >= 7) 200 else 90 + edge * 15)
        }
        assertEquals(7, bakedFrameInset(150, pixels))
    }

    @Test fun keepsPlainAndTransparentImages() {
        assertEquals(0, bakedFrameInset(150, image(150) { _, _ -> gray(180) }))
        assertEquals(0, bakedFrameInset(150, image(150) { x, y ->
            val edge = minOf(x, y, 149 - x, 149 - y)
            gray(if (edge >= 7) 200 else 90 + edge * 15, if (edge == 0) 0 else 255)
        }))
    }

    @Test fun ignoresSlightEdgeShadingAndTinyImages() {
        val soft = image(150) { x, y -> gray(if (minOf(x, y, 149 - x, 149 - y) >= 4) 200 else 190) }
        assertEquals(0, bakedFrameInset(150, soft))
        assertEquals(0, bakedFrameInset(10, image(10) { _, _ -> gray(100) }))
    }
}
