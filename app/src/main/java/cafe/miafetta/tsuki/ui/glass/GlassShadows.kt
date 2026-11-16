package cafe.miafetta.tsuki.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
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
 * Where the chips of a GameChips row are, for the row to draw the selected chip's shadow ([chipRowShadow]). The chips
 * write their coordinates after every layout pass in which they may have moved; the row reads them while drawing.
 */
internal class ChipShadowAnchor {
    private val chips = HashMap<String, LayoutCoordinates>()
    internal var moved: () -> Unit = {}

    fun place(id: String, coordinates: LayoutCoordinates) {
        chips[id] = coordinates
        moved()
    }

    fun coordinates(id: String): LayoutCoordinates? = chips[id]?.takeIf { it.isAttached }
}

// On every chip: reports its place whenever it may have changed, scrolling and overscroll included.
internal fun Modifier.chipShadowAnchor(anchor: ChipShadowAnchor, id: String): Modifier =
    onGloballyPositioned { anchor.place(id, it) }

/**
 * The selected chip's shadow, drawn by the row under its content. Only the selected chip casts one, at rest, as glass and
 * with blur off, like the theme store's; with two or more chips a shadow under the unselected ones smeared into the content
 * below the row, and their edge light keeps them apart from the page.
 *
 * A shadow drawn by the chip itself is cut at the row's bounds, 4dp above and 10dp below the chip: MiuixTheme provides
 * MiuixOverscrollFactory as LocalOverscrollFactory, and its node places the scrolled content in a layer with clip = true
 * (miuix-ui utils/OverscrollFactory.kt, MiuixOverscrollEffectNode.measure). Apply this before horizontalScroll, so it draws
 * outside that layer; it adds no layout or pointer input. The shadow is clipped to the row's width, where the chips show.
 */
@Composable
internal fun Modifier.chipRowShadow(anchor: ChipShadowAnchor, selectedId: String): Modifier =
    this then ChipRowShadowElement(anchor, selectedId, glassShadowColor())

private data class ChipRowShadowElement(val anchor: ChipShadowAnchor, val selectedId: String, val color: Color) :
    ModifierNodeElement<ChipRowShadowNode>() {
    override fun create() = ChipRowShadowNode(anchor, selectedId, color)

    override fun update(node: ChipRowShadowNode) = node.update(anchor, selectedId, color)

    override fun InspectorInfo.inspectableProperties() {
        name = "chipRowShadow"
        properties["selectedId"] = selectedId
    }
}

private val ChipShadowShape = RoundedCornerShape(ChipCornerRadius)

private class ChipRowShadowNode(private var anchor: ChipShadowAnchor, private var selectedId: String, private var color: Color) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var painter: Painter? = null
    private var drawn: Rect? = null
    private val outline = Path()
    private var outlineSize = Size.Unspecified

    override fun onAttach() {
        anchor.moved = ::chipMoved
    }

    override fun onDetach() {
        anchor.moved = {}
        painter = null
    }

    fun update(anchor: ChipShadowAnchor, selectedId: String, color: Color) {
        if (anchor !== this.anchor) {
            this.anchor.moved = {}
            this.anchor = anchor
            if (isAttached) anchor.moved = ::chipMoved
        }
        if (color != this.color) painter = null
        this.color = color
        this.selectedId = selectedId
        invalidateDraw()
    }

    // The chips report after every layout pass; redraw only when the selected one moved against the row. Layout callbacks
    // run before this frame draws, so the shadow keeps up with the chip.
    private fun chipMoved() {
        if (isAttached && chipRect() != drawn) invalidateDraw()
    }

    private fun chipRect(): Rect? {
        val chip = anchor.coordinates(selectedId) ?: return null
        val row = requireLayoutCoordinates()
        return if (row.isAttached) row.localBoundingBoxOf(chip, clipBounds = false) else null
    }

    override fun ContentDrawScope.draw() {
        val rect = chipRect()
        drawn = rect
        if (rect != null) drawShadow(rect)
        drawContent()
    }

    private fun DrawScope.drawShadow(rect: Rect) {
        // The shared shadow context caches the blurred bitmap per size, as Modifier.dropShadow does.
        val shadow = painter ?: currentValueOf(LocalGraphicsContext).shadowContext.createDropShadowPainter(ChipShadowShape,
            Shadow(radius = ChipShadowRadius, color = color, alpha = ChipShadowAlpha)).also { painter = it }
        if (outlineSize != rect.size) {
            outline.rewind()
            outline.addOutline(ChipShadowShape.createOutline(rect.size, layoutDirection, this))
            outlineSize = rect.size
        }
        val reach = ChipShadowRadius.toPx() + 1f
        clipRect(0f, rect.top - reach, size.width, rect.bottom + reach) {
            translate(rect.left, rect.top) {
                clipPath(outline, ClipOp.Difference) { with(shadow) { draw(rect.size) } }
            }
        }
    }
}
