package cafe.miafetta.tsuki.ui.glass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

// HyperOS 4 system glass (the Phone app dock and SIM switcher, the theme store chips; recorded 2026-11-14) lights the inside of
// its outline additively: the rim is a fixed number of levels brighter than whatever the body shows, the same in light and
// dark, 2-3 px wide at 3.25 px/dp. It is drawn with BlendMode.Plus, always inside the outline (the stroke's centre line is
// inset by half its width), so measurement, layout and hit areas stay as they are.

// White is the colour of the light itself, not a theme colour: Plus adds it to the pixels already drawn.
private val EdgeLightColor = Color.White

// The theme store's unselected glass chip, by the outward normal of its outline: straight top and bottom edges .135, both
// ends .09, the top-left and bottom-right turns .16, the other two turns .09. Symmetric under a half turn; 90 and 270 meet
// the straight edges.
private val CapsuleLightAngles = floatArrayOf(0f, 60f, 90f, 150f, 180f, 240f, 270f, 330f, 360f)
private val CapsuleLightAlphas = floatArrayOf(.09f, .16f, .135f, .09f, .09f, .16f, .135f, .09f, .09f)
internal const val CapsuleLightStraight = .135f

/**
 * Rim alpha of a capsule at an outward normal in screen degrees: 0 points right, angles turn clockwise, 90 points down.
 * [reduction] is the rim the glass material already brings, taken off every direction (never below 0) so the sum matches.
 */
internal fun capsuleEdgeLightAlpha(normalDegrees: Float, reduction: Float = 0f): Float {
    if (!normalDegrees.isFinite()) return (CapsuleLightStraight - reduction).coerceAtLeast(0f)
    val angle = (normalDegrees % 360f + 360f) % 360f
    val end = CapsuleLightAngles.indexOfFirst { it >= angle }.coerceAtLeast(1)
    val from = CapsuleLightAngles[end - 1]
    val t = (angle - from) / (CapsuleLightAngles[end] - from)
    val alpha = CapsuleLightAlphas[end - 1] + (CapsuleLightAlphas[end] - CapsuleLightAlphas[end - 1]) * t
    return (alpha - reduction).coerceAtLeast(0f)
}

// Brush.sweepGradient begins at 3 o'clock and turns clockwise (its KDoc). On Android it is android.graphics.SweepGradient,
// whose Skia stage (xy_to_unit_angle) measures the angle from +x towards +y, and +y points down on screen. So a stop at
// fraction f lies at the normal angle f * 360 in the convention above, and the stops are the table's own break points.
// The shader reduces at the break points and then interpolates; capsuleEdgeLightAlpha interpolates and then reduces. The two
// agree while the reduction stays at or below the table's smallest alpha (.09), since no segment is then clamped.
internal fun capsuleEdgeLightStops(reduction: Float = 0f): List<Pair<Float, Float>> =
    CapsuleLightAngles.map { it / 360f to capsuleEdgeLightAlpha(it, reduction) }

/** Draws a rim whose centre line is [path] and whose width is the [stroke]'s. */
internal fun DrawScope.drawEdgeLight(path: Path, stroke: Stroke, alpha: Float) {
    if (alpha > 0f) drawPath(path, EdgeLightColor, alpha = alpha.coerceAtMost(1f), style = stroke, blendMode = BlendMode.Plus)
}

/** A uniform rim inside a rounded rectangle of [cornerRadius] (clamped to a capsule); rebuilt only when the size changes. */
internal class UniformEdgeLight(private val alpha: Float, private val width: Dp, private val cornerRadius: Dp) {
    private val path = Path()
    private var stroke = Stroke(0f)
    private var cachedSize = Size.Unspecified
    private var cachedDensity = 0f

    fun draw(scope: DrawScope, strength: Float) {
        if (strength <= 0f) return
        with(scope) {
            if (size != cachedSize || density != cachedDensity) {
                val widthPx = width.toPx()
                val inset = widthPx / 2f
                path.rewind()
                if (size.width > widthPx && size.height > widthPx) {
                    val radius = (minOf(cornerRadius.toPx(), size.minDimension / 2f) - inset).coerceAtLeast(0f)
                    path.addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(radius)))
                }
                stroke = Stroke(widthPx)
                cachedSize = size
                cachedDensity = density
            }
            drawEdgeLight(path, stroke, alpha * strength)
        }
    }
}

/**
 * The theme store chip's directional rim around a horizontal capsule (corner radius = half its height): the straight edges
 * in one colour, each end as an arc whose sweep gradient is centred on that end's circle, so the gradient angle is the
 * outline's outward normal. The pieces abut with butt caps at the ends' 90 and 270 degree points; rebuilt per size.
 * [reduction] is passed to capsuleEdgeLightAlpha; the colour stops are made once per instance.
 */
internal class CapsuleEdgeLight(private val width: Dp, reduction: Float = 0f) {
    private val straightAlpha = capsuleEdgeLightAlpha(90f, reduction)
    private val colorStops = capsuleEdgeLightStops(reduction).map { (fraction, alpha) ->
        fraction to EdgeLightColor.copy(alpha = alpha)
    }.toTypedArray()
    private val straight = Path()
    private val leftEnd = Path()
    private val rightEnd = Path()
    private var leftBrush: Brush = SolidColor(Color.Transparent)
    private var rightBrush: Brush = SolidColor(Color.Transparent)
    private var stroke = Stroke(0f)
    private var cachedSize = Size.Unspecified
    private var cachedDensity = 0f

    fun draw(scope: DrawScope, strength: Float) {
        if (strength <= 0f) return
        with(scope) {
            if (size.width < size.height || size.height <= width.toPx()) return
            if (size != cachedSize || density != cachedDensity) rebuild()
            val alpha = strength.coerceAtMost(1f)
            drawEdgeLight(straight, stroke, straightAlpha * alpha)
            drawPath(leftEnd, leftBrush, alpha = alpha, style = stroke, blendMode = BlendMode.Plus)
            drawPath(rightEnd, rightBrush, alpha = alpha, style = stroke, blendMode = BlendMode.Plus)
        }
    }

    private fun DrawScope.rebuild() {
        val widthPx = width.toPx()
        val radius = size.height / 2f
        val inset = widthPx / 2f
        val left = Offset(radius, radius)
        val right = Offset(size.width - radius, radius)
        straight.rewind()
        if (right.x > left.x) {
            straight.moveTo(left.x, inset)
            straight.lineTo(right.x, inset)
            straight.moveTo(left.x, size.height - inset)
            straight.lineTo(right.x, size.height - inset)
        }
        // Path arcs share the sweep convention: 0 degrees at the right of the oval, positive sweeps clockwise.
        leftEnd.rewind()
        leftEnd.addArc(Rect(left, radius - inset), 90f, 180f)
        rightEnd.rewind()
        rightEnd.addArc(Rect(right, radius - inset), -90f, 180f)
        leftBrush = Brush.sweepGradient(*colorStops, center = left)
        rightBrush = Brush.sweepGradient(*colorStops, center = right)
        stroke = Stroke(widthPx)
        cachedSize = size
        cachedDensity = density
    }
}
