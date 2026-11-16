package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class GlassSurface { Button, Chip, Dock }

// Edge lights measured on 2026-11-14 (see EdgeLight.kt): the Phone app dock has a uniform rim 26 levels brighter, 2 px wide;
// the theme store chip's rim is 2-3 px wide and its strength follows the outline (capsuleEdgeLightAlpha).
private const val DockEdgeLightAlpha = .10f
private val DockEdgeLightWidth = .6.dp
private val ChipEdgeLightWidth = .75.dp

// Light MiGlass adds a rim of its own over content, none on miuix-blur or on black, so that path takes the material's share
// off every direction; it acts additively. Second device reading, 2026-11-15, with the dock's material (65%, radius 14):
// with .078 the straight top edge summed to +28.9 against the theme store's +31 to +35, because the denser tint covers
// part of the material's own rim; .062 (range .054-.070) brings it back to about +33. Dark pages are mostly black, where
// the material adds nothing.
internal const val NativeLightChipEdgeLightReduction = .062f

// The chip's outline; the selected chip's shadow (GlassShadows.kt) uses the same shape.
internal val ChipCornerRadius = 19.dp

internal fun chipEdgeLightReduction(native: Boolean, dark: Boolean): Float =
    if (native && !dark) NativeLightChipEdgeLightReduction else 0f

@Composable
internal fun dockSelectionColor(): Color = if (LocalGlass.current?.available == true)
    MiuixTheme.colorScheme.onSurface.copy(alpha = if (isTsukiDarkTheme()) .12f else .06f)
    else MiuixTheme.colorScheme.secondaryContainer

// The selected chip is always the inverse of the page (dark on light, light on dark) so it stays distinguishable from
// unselected chips. Its tone follows the resting theme store chip (2026-11-14): about 57 on its light page, 230 on black.
internal fun chipSelectionColors(colors: Colors, dark: Boolean): Pair<Color, Color> =
    colors.onSurface.copy(alpha = if (dark) .95f else .77f).compositeOver(colors.surface) to colors.surface

@Composable
internal fun chipSelectionColors(): Pair<Color, Color> = chipSelectionColors(MiuixTheme.colorScheme, isTsukiDarkTheme())

// An unselected chip at rest. On black the theme store chip reads about 16, darker than surfaceContainerHigh (36), so dark
// mixes back towards the surface; lerp mixes in Oklab, hence the large fraction. Light keeps surfaceContainerHigh.
internal fun chipIdleColor(colors: Colors, dark: Boolean): Color =
    if (dark) lerp(colors.surface, colors.surfaceContainerHigh, .66f) else colors.surfaceContainerHigh

// Button and dock tint. Dock values follow the HyperOS 4 Phone app dock as measured on 2026-11-13: the light tint of 65% sits
// between the large-area (about 69%) and detail (about 62%) see-through, and miuix-blur keeps the native ratio. On a black
// page the system dock body reads 21 (Phone app and theme store alike, 2026-11-14); 52% of the dark base gives that.
internal fun glassTint(colors: Colors, dark: Boolean, surface: GlassSurface, native: Boolean): Color {
    val base = if (dark) lerp(colors.surfaceContainer, colors.onSurface, .02f) else colors.surfaceContainer
    return base.copy(alpha = when {
        surface == GlassSurface.Button -> .62f
        dark -> .52f
        surface == GlassSurface.Dock && native -> .65f
        else -> .41f
    })
}

// A chip once the page scrolls under it. Unselected chips switch to the dock's material, as the theme store's do (2026-11-14,
// 0.63-0.68 white on light): the light tint is the dock's on each backend, and dark keeps 55% of its slightly lifted base,
// within the system's 0.51-0.55. The selected chip stays its resting colour: opaque on light (the system shows nothing
// through it), 94% on dark (about 6.5% shows through).
internal fun chipGlassTint(colors: Colors, dark: Boolean, selected: Boolean, native: Boolean): Color = when {
    selected -> chipSelectionColors(colors, dark).first.copy(alpha = if (dark) .94f else 1f)
    dark -> lerp(colors.surfaceContainer, colors.onSurface, .04f).copy(alpha = .55f)
    else -> glassTint(colors, false, GlassSurface.Dock, native)
}

// Blur radius of each surface on both backends: dp on miuix-blur, the MiGlass radius on the native path (GlassBackground).
// The dock's 14 matches the blur of the HyperOS 4 Phone app dock (2026-11-13, sigma about 25 px). Unselected chips take the
// dock's material and so its radius: the theme store chip's sigma could not be measured (2026-11-14), so this one needs a
// device calibration. The button and the selected chip keep 20.
internal fun surfaceBlurRadius(surface: GlassSurface, selected: Boolean = false): Float = when (surface) {
    GlassSurface.Dock -> 14f
    GlassSurface.Button -> 20f
    GlassSurface.Chip -> if (selected) 20f else 14f
}

@Composable
internal fun glassShadowColor(): Color = if (isTsukiDarkTheme()) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.onSurface

@Composable
internal fun Modifier.tsukiGlass(surface: GlassSurface, progress: Float = 1f, selected: Boolean = false,
    native: Boolean = false): Modifier {
    if (surface == GlassSurface.Chip) return chipGlass(progress, selected, native)
    val glass = LocalGlass.current
    val dark = isTsukiDarkTheme()
    val colors = MiuixTheme.colorScheme
    val shadowColor = glassShadowColor()
    val radius = if (surface == GlassSurface.Dock) 26.dp else 20.dp
    val shape = RoundedCornerShape(radius)
    val currentProgress by rememberUpdatedState(progress.coerceIn(0f, 1f))
    val light = remember(surface) {
        if (surface == GlassSurface.Dock) UniformEdgeLight(DockEdgeLightAlpha, DockEdgeLightWidth, radius) else null
    }
    if (glass == null || (!glass.available && !native)) return (if (surface == GlassSurface.Dock) this else
        shadow(2.dp, shape, clip = false,
        ambientColor = shadowColor.copy(alpha = .08f * (if (surface == GlassSurface.Button) progress else 1f)),
        spotColor = shadowColor.copy(alpha = .08f * (if (surface == GlassSurface.Button) progress else 1f))))
        .drawBehind { drawRoundRect(colors.surfaceContainer, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            alpha = if (surface == GlassSurface.Button) currentProgress else 1f) }
    val blurRadius = surfaceBlurRadius(surface).dp
    val saturation = if (surface == GlassSurface.Dock) 1.8f else 1.6f
    val tint = glassTint(colors, dark, surface, native)
    // Only the button keeps a 1dp outline (on miuix-blur); the dock has the system's edge light instead.
    val rim = if (surface == GlassSurface.Button) (if (dark) colors.onSurface else colors.surfaceContainer)
        .copy(alpha = if (dark) .10f else .95f) else null
    val overlay: DrawScope.() -> Unit = {
        glassOverlay(currentProgress, native, radius, tint.copy(alpha = tint.alpha * currentProgress), rim)
    }
    val edge: (DrawScope.() -> Unit)? = if (light == null) null else { { light.draw(this, currentProgress) } }
    return (if (surface == GlassSurface.Dock) this else
        shadow(4.dp, shape, clip = false, ambientColor = shadowColor.copy(alpha = .08f * progress),
        spotColor = shadowColor.copy(alpha = .08f * progress)))
        .glassLayer(glass, native, shape, blurRadius, saturation, overlay, edge)
}

@Composable
private fun Modifier.chipGlass(progress: Float, selected: Boolean, native: Boolean): Modifier {
    val glass = LocalGlass.current
    val dark = isTsukiDarkTheme()
    val colors = MiuixTheme.colorScheme
    val radius = ChipCornerRadius
    val shape = RoundedCornerShape(radius)
    val currentProgress by rememberUpdatedState(progress.coerceIn(0f, 1f))
    // Keyed on both inputs so a theme or backend switch swaps in the matching rim instead of keeping the old one.
    val light = remember(native, dark) { CapsuleEdgeLight(ChipEdgeLightWidth, chipEdgeLightReduction(native, dark)) }
    val idle = if (selected) chipSelectionColors(colors, dark).first else chipIdleColor(colors, dark)
    if (glass == null || (!glass.available && !native)) return background(idle, shape)
    val glassTint = chipGlassTint(colors, dark, selected, native)
    // The selected chip's shadow is drawn by the chip row (chipRowShadow); unselected chips cast none. The edge light,
    // which grows with the glass like the theme store's, keeps an unselected chip apart from the page; the selected chip has
    // none.
    val overlay: DrawScope.() -> Unit = {
        glassOverlay(currentProgress, native, radius, lerp(idle, glassTint, currentProgress), null)
    }
    // Checked while drawing, so selecting a chip does not change its modifier chain.
    val edge: DrawScope.() -> Unit = { if (!selected) light.draw(this, currentProgress) }
    return glassLayer(glass, native, shape, surfaceBlurRadius(GlassSurface.Chip, selected).dp, if (selected) 1.6f else 1.8f,
        overlay, edge)
}

// The native path draws over the MiGlass view on the parent canvas. drawBackdrop draws into its own offscreen layer clipped
// to the shape, where the glass is not yet composited over the page; Plus there would add only to that layer, and where it
// is translucent the light would fade on light pages. So the edge light is drawn by a node outside drawBackdrop, after its
// layer, and only while drawBackdrop is enabled (glass.active), so there is no rim without glass.
private fun Modifier.glassLayer(glass: GlassContext, native: Boolean, shape: Shape, blurRadius: Dp, saturation: Float,
    overlay: DrawScope.() -> Unit, edge: (DrawScope.() -> Unit)?): Modifier {
    if (native) return drawBehind {
        overlay()
        edge?.invoke(this)
    }
    val afterLayer = if (edge == null) this else drawWithContent {
        drawContent()
        if (glass.active) edge()
    }
    return afterLayer.drawBackdrop(glass.backdrop, shape = { shape }, effects = {
        blur(blurRadius.toPx())
        colorControls(saturation = saturation)
    }, enabled = glass.active, onDrawSurface = overlay)
}

private fun DrawScope.glassOverlay(progress: Float, native: Boolean, radius: Dp, tint: Color, rim: Color?) {
    if (!native) drawRect(Color.White.copy(alpha = progress), blendMode = BlendMode.DstIn)
    val corner = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
    drawRoundRect(tint, cornerRadius = corner)
    if (rim != null && !native) drawRoundRect(rim.copy(alpha = rim.alpha * progress),
        cornerRadius = corner, style = Stroke(1.dp.toPx()))
}
