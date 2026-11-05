package cafe.miafetta.tsuki.widget

// Official player avatars bake a dark inner frame into the image; under a rounded crop it reads as a black rim.
// Only fully opaque edges that brighten steadily inwards are treated as a frame; at most 10% per side is trimmed.
internal fun bakedFrameInset(size: Int, argb: IntArray): Int {
    val limit = size / 10
    if (limit < 2 || argb.size < size * size) return 0
    fun ring(offset: Int): Double? {
        var total = 0L
        var count = 0
        for (t in offset until size - offset) {
            for (color in intArrayOf(argb[offset * size + t], argb[(size - 1 - offset) * size + t],
                argb[t * size + offset], argb[t * size + size - 1 - offset])) {
                if (color ushr 24 < 250) return null
                total += ((color shr 16 and 255) * 299 + (color shr 8 and 255) * 587 + (color and 255) * 114) / 1000
                count++
            }
        }
        return total.toDouble() / count
    }
    val rings = (0..limit).map { ring(it) ?: return 0 }
    val settled = (0 until limit).firstOrNull { rings[it + 1] - rings[it] < 3.0 } ?: return 0
    return if (settled > 0 && rings[settled] - rings[0] >= 30.0) settled else 0
}
