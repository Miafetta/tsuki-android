package cafe.miafetta.tsuki.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider

// Fitted to the original recording PTS
// these are not HyperOS spring constants.
internal fun menuContentBlurRadius(visibility: Float): Float =
    if (visibility <= 0f || visibility >= 1f) 0f else 6f * (1f - visibility)

internal val MenuCloseEasing = CubicBezierEasing(0f, .5f, .4f, 1f)

// Icon-button menus: system clock app recordings (2026-10-31).
internal const val MenuOpenMillis = 330
internal const val MenuCloseMillis = 170
private val MenuTimes = floatArrayOf(0f, .17f, .37f, .57f, .77f, 1f)
private val MenuWidths = floatArrayOf(.24f, .42f, .73f, .91f, .99f, 1f)
private val MenuHeights = floatArrayOf(.45f, .58f, .82f, .96f, 1f, 1f)
private val MenuOffsets = floatArrayOf(0f, .8f, 1f, .5f, .1f, 0f)

// Dropdown fields (preference values, text triggers): system launcher settings recording (2026-11-06).
internal const val FieldMenuOpenMillis = 270
internal const val FieldMenuCollapseMillis = 130
internal const val FieldMenuCloseMillis = 190
internal const val FieldContentInMillis = 50
internal const val FieldTriggerFadeOutMillis = 70
internal const val FieldTriggerFadeInMillis = 80
internal const val FieldHoldAlpha = .1f
internal val FieldPillHeight = 24.dp
internal val FieldMenuDrift = 9.dp
internal val FieldMenuOverlap = 14.dp
internal const val FieldStartWidth = .68f
private val FieldTimes = floatArrayOf(0f, .08f, .2f, .4f, .65f, .9f, 1f)
private val FieldWidths = floatArrayOf(FieldStartWidth, .72f, .78f, .89f, .965f, 1f, 1f)
// Share of the way from the fixed-height pill to the full panel.
private val FieldHeights = floatArrayOf(0f, .095f, .31f, .66f, .89f, 1f, 1f)
private val FieldOffsets = floatArrayOf(.15f, .45f, .85f, 1f, .45f, .05f, 0f)

internal data class MenuMotion(val width: Float, val height: Float, val offset: Float)

private fun sample(times: FloatArray, progress: Float, vararg tracks: FloatArray): List<Float> {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val i = (times.indexOfFirst { it >= p }.coerceAtLeast(1) - 1).coerceAtMost(times.lastIndex - 1)
    val fraction = (p - times[i]) / (times[i + 1] - times[i])
    return tracks.map { it[i] + (it[i + 1] - it[i]) * fraction }
}

internal fun menuMotion(progress: Float): MenuMotion {
    val (width, height, offset) = sample(MenuTimes, progress, MenuWidths, MenuHeights, MenuOffsets)
    return MenuMotion(width, height, offset)
}

/** [startHeight] is the pill height as a fraction of the full panel height. */
internal fun fieldMenuMotion(progress: Float, startHeight: Float): MenuMotion {
    val (width, height, offset) = sample(FieldTimes, progress, FieldWidths, FieldHeights, FieldOffsets)
    val start = if (startHeight.isFinite()) startHeight.coerceIn(0f, 1f) else 1f
    return MenuMotion(width, start + (1f - start) * height, offset)
}

internal class MenuShape(private val motion: MenuMotion, private val startWidth: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // Keep circular corners while the panel expands at different rates on each axis.
        val growth = ((1f - motion.width) / (1f - startWidth)).coerceIn(0f, 1f)
        val radius = minOf(with(density) { (20f + 4f * growth).dp.toPx() },
            size.width * motion.width / 2, size.height * motion.height / 2)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height,
            CornerRadius(radius / motion.width, radius / motion.height)))
    }
}

/**
 * Icon-button menus ([fieldOverlap] < 0) cover the trigger from its top or bottom edge. Dropdown fields reach
 * [fieldOverlap] beyond the trigger's centre line instead, and [onAnchor] reports the trigger in popup coordinates.
 */
internal class TsukiMenuPosition(private val rightAligned: Boolean, private val safeInsets: IntRect = IntRect.Zero,
    private val shadowPad: Int = 0, private val fieldOverlap: Int = -1, private val onAnchor: (IntRect) -> Unit = {},
    private val onPosition: (Boolean) -> Unit = {}) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val bounds = IntRect(safeInsets.left, safeInsets.top, windowSize.width - safeInsets.right, windowSize.height - safeInsets.bottom)
        val panel = IntSize((popupContentSize.width - 2 * shadowPad).coerceAtLeast(0),
            (popupContentSize.height - 2 * shadowPad).coerceAtLeast(0))
        val top = if (fieldOverlap < 0) anchorBounds.top else anchorBounds.center.y - fieldOverlap
        val bottom = if (fieldOverlap < 0) anchorBounds.bottom else anchorBounds.center.y + fieldOverlap
        val above = top + panel.height > bounds.bottom
        val x = if (rightAligned) anchorBounds.right - panel.width else anchorBounds.left
        val y = if (above) bottom - panel.height else top
        onPosition(above)
        val origin = IntOffset(x.coerceIn(bounds.left, (bounds.right - panel.width).coerceAtLeast(bounds.left)) - shadowPad,
            y.coerceIn(bounds.top, (bounds.bottom - panel.height).coerceAtLeast(bounds.top)) - shadowPad)
        onAnchor(anchorBounds.translate(-origin.x, -origin.y))
        return origin
    }
}
