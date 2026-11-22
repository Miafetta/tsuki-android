package cafe.miafetta.tsuki.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Outer shadows, fitted to the HyperOS 4 theme store on 2026-11-14 (light, darkening against the page, +-2 levels): the dock
// -8.5 at its top edge, -5 at 40 px and -3.1 at 80 px, -7 beside its ends falling to -2 at 60 px, not offset downwards; the
// selected chip -9 at its edge, gone about 50 px out, the same above and below. Compose's dropShadow blurs through
// BlurMaskFilter(radius), sigma 0.577 x radius + 0.5 px (the dock's device profile fits this, 2026-11-15), and stops radius
// outside the outline's bounds. On black pages the black shadow shows nothing, as on the system. The dock matched its
// readings within 1.5 levels on the device; the chip's strength matched, its reach still needs the device.
private val DockShadowRadius = 36.5.dp
private const val DockShadowAlpha = .081f
internal val ChipShadowRadius = 15.dp
private const val ChipShadowAlpha = .087f

internal fun chipShadowStrength(selected: Boolean, unselectedProgress: Float, unselectedGlassEnabled: Boolean): Float = when {
    selected -> 1f
    !unselectedGlassEnabled || !unselectedProgress.isFinite() -> 0f
    else -> unselectedProgress.coerceIn(0f, 1f)
}

// Apply to a background-only sibling; the same shadow on every backend, outside the capsule only.
@Composable
internal fun Modifier.dockShadow(): Modifier {
    val color = glassShadowColor()
    return then(remember(color) { Modifier.outerShadow(RoundedCornerShape(26.dp), DockShadowRadius, DockShadowAlpha, color) })
}

// Everything inside the outline is clipped away: there the shadow would show through translucent glass (the native view or
// the backdrop's alpha mask) and grey the surface, which HyperOS system glass does not do. Measurement, layout and hit areas
// stay those of the surface.
private fun Modifier.outerShadow(shape: Shape, shadowRadius: Dp, shadowAlpha: Float, shadowColor: Color): Modifier =
    drawWithContent {
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        clipPath(outline, ClipOp.Difference) { this@drawWithContent.drawContent() }
    }.dropShadow(shape) {
        radius = shadowRadius.toPx()
        color = shadowColor
        alpha = shadowAlpha
    }

/**
 * Where the chips of a GameChips row are, for the row to draw chip shadows ([chipRowShadow]). The chips write their
 * coordinates after every layout pass in which they may have moved; the row reads them while drawing.
 */
internal class ChipShadowAnchor {
    private val chips = LinkedHashMap<String, LayoutCoordinates>()
    internal var moved: () -> Unit = {}

    fun place(id: String, coordinates: LayoutCoordinates) {
        chips[id] = coordinates
        moved()
    }

    fun coordinates(id: String): LayoutCoordinates? = chips[id]?.takeIf { it.isAttached }

    fun attached(): List<Pair<String, LayoutCoordinates>> =
        chips.entries.mapNotNull { (id, coordinates) -> if (coordinates.isAttached) id to coordinates else null }
}

// On every chip: reports its place whenever it may have changed, scrolling and overscroll included.
internal fun Modifier.chipShadowAnchor(anchor: ChipShadowAnchor, id: String): Modifier =
    onGloballyPositioned { anchor.place(id, it) }

/**
 * Chip shadows, drawn by the row under its content. The selected chip casts its shadow at rest, as glass and with blur off;
 * unselected chips cast no shadow at rest or with blur off, and fade in the same symmetric outer shadow over [progress] once
 * scrolled into glass mode so they remain distinct over `#FFFFFF` cards alongside their additive rim light. All attached
 * chip interiors are clipped out so no chip's shadow darkens any translucent surface.
 *
 * A shadow drawn by the chip itself is cut at the row's bounds, 4dp above and 10dp below the chip: MiuixTheme provides
 * MiuixOverscrollFactory as LocalOverscrollFactory, and its node places the scrolled content in a layer with clip = true
 * (miuix-ui utils/OverscrollFactory.kt, MiuixOverscrollEffectNode.measure). Apply this before horizontalScroll, so it draws
 * outside that layer; it adds no layout or pointer input. The shadow is clipped to the row's width, where the chips show.
 */
@Composable
internal fun Modifier.chipRowShadow(anchor: ChipShadowAnchor, selectedId: String,
    unselectedGlass: Boolean = false, progress: () -> Float = { 0f }): Modifier =
    this then ChipRowShadowElement(anchor, selectedId, glassShadowColor(), unselectedGlass, progress)

private data class ChipRowShadowElement(val anchor: ChipShadowAnchor, val selectedId: String, val color: Color,
    val unselectedGlass: Boolean, val progress: () -> Float) : ModifierNodeElement<ChipRowShadowNode>() {
    override fun create() = ChipRowShadowNode(anchor, selectedId, color, unselectedGlass, progress)

    override fun update(node: ChipRowShadowNode) = node.update(anchor, selectedId, color, unselectedGlass, progress)

    override fun InspectorInfo.inspectableProperties() {
        name = "chipRowShadow"
        properties["selectedId"] = selectedId
        properties["unselectedGlass"] = unselectedGlass
    }
}

private val ChipShadowShape = RoundedCornerShape(ChipCornerRadius)

private class ChipRowShadowNode(private var anchor: ChipShadowAnchor, private var selectedId: String, private var color: Color,
    private var unselectedGlass: Boolean, private var progress: () -> Float) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var painter: Painter? = null
    private var drawn: List<Pair<String, Rect>> = emptyList()
    private val allOutlines = Path()
    private var cachedRects: List<Rect> = emptyList()
    private var cachedDensity = 0f

    override fun onAttach() {
        anchor.moved = ::chipMoved
    }

    override fun onDetach() {
        anchor.moved = {}
        painter = null
    }

    fun update(anchor: ChipShadowAnchor, selectedId: String, color: Color, unselectedGlass: Boolean, progress: () -> Float) {
        if (anchor !== this.anchor) {
            this.anchor.moved = {}
            this.anchor = anchor
            if (isAttached) anchor.moved = ::chipMoved
        }
        if (color != this.color) painter = null
        this.color = color
        this.selectedId = selectedId
        this.unselectedGlass = unselectedGlass
        this.progress = progress
        invalidateDraw()
    }

    // The chips report after every layout pass; redraw only when any attached chip moved against the row. Layout callbacks
    // run before this frame draws, so the shadows keep up with the chips.
    private fun chipMoved() {
        if (isAttached && chipRects() != drawn) invalidateDraw()
    }

    private fun chipRects(): List<Pair<String, Rect>> {
        val row = requireLayoutCoordinates()
        if (!row.isAttached) return emptyList()
        return anchor.attached().map { (id, chip) -> id to row.localBoundingBoxOf(chip, clipBounds = false) }
    }

    override fun ContentDrawScope.draw() {
        val entries = chipRects()
        drawn = entries
        if (entries.isNotEmpty()) drawShadows(entries)
        drawContent()
    }

    private fun DrawScope.drawShadows(entries: List<Pair<String, Rect>>) {
        val unselectedProgress = if (unselectedGlass) progress() else 0f
        if (!entries.any { (id, rect) ->
            !rect.isEmpty && chipShadowStrength(id == selectedId, unselectedProgress, unselectedGlass) > 0f
        }) return
        val shadow = painter ?: currentValueOf(LocalGraphicsContext).shadowContext.createDropShadowPainter(ChipShadowShape,
            Shadow(radius = ChipShadowRadius, color = color, alpha = ChipShadowAlpha)).also { painter = it }
        updateCutout(entries)
        val minTop = entries.minOf { it.second.top }
        val maxBottom = entries.maxOf { it.second.bottom }
        val reach = ChipShadowRadius.toPx() + 1f
        clipRect(0f, minTop - reach, size.width, maxBottom + reach) {
            clipPath(allOutlines, ClipOp.Difference) {
                for ((id, rect) in entries) {
                    if (rect.isEmpty) continue
                    val strength = chipShadowStrength(id == selectedId, unselectedProgress, unselectedGlass)
                    if (strength > 0f) translate(rect.left, rect.top) { with(shadow) { draw(rect.size, alpha = strength) } }
                }
            }
        }
    }

    private fun DrawScope.updateCutout(entries: List<Pair<String, Rect>>) {
        val rects = entries.map { it.second }
        if (rects == cachedRects && density == cachedDensity) return
        allOutlines.rewind()
        for (rect in rects) {
            val corner = CornerRadius(minOf(ChipCornerRadius.toPx(), rect.minDimension / 2f))
            allOutlines.addRoundRect(RoundRect(rect, corner))
        }
        cachedRects = rects
        cachedDensity = density
    }
}
