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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class GlassSurface { Button, Chip, Dock }

@Composable
internal fun dockSelectionColor(): Color = if (LocalGlass.current?.available == true)
    MiuixTheme.colorScheme.onSurface.copy(alpha = if (isTsukiDarkTheme()) .12f else .06f)
    else MiuixTheme.colorScheme.secondaryContainer

@Composable
// The selected chip is always the inverse of the page (dark on light, light on dark) so it stays
// distinguishable from unselected chips, which use surfaceContainerHigh at rest.
internal fun chipSelectionColors(): Pair<Color, Color> =
    MiuixTheme.colorScheme.onSurface.copy(alpha = .8f).compositeOver(MiuixTheme.colorScheme.surface) to MiuixTheme.colorScheme.surface

@Composable
internal fun glassShadowColor(): Color = if (isTsukiDarkTheme()) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.onSurface

// Apply to a background-only sibling: everything inside the capsule outline is clipped away.
@Composable
internal fun Modifier.dockShadow(): Modifier {
    val shadowColor = glassShadowColor()
    val shape = RoundedCornerShape(26.dp)
    // Keep the external shadow outside the native view and backdrop alpha mask. Its interior would show through the
    // translucent glass and grey the capsule, which HyperOS system docks do not do.
    return drawWithContent {
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        clipPath(outline, ClipOp.Difference) { this@drawWithContent.drawContent() }
    }.dropShadow(shape) {
        radius = 18.dp.toPx()
        offset = androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx())
        color = shadowColor
        alpha = .08f
    }
}

@Composable
internal fun Modifier.tsukiGlass(surface: GlassSurface, progress: Float = 1f, selected: Boolean = false,
    native: Boolean = false): Modifier {
    val glass = LocalGlass.current
    val dark = isTsukiDarkTheme()
    val colors = MiuixTheme.colorScheme
    val shadowColor = glassShadowColor()
    val radius = when (surface) {
        GlassSurface.Dock -> 26.dp
        GlassSurface.Chip -> 19.dp
        GlassSurface.Button -> 20.dp
    }
    val shape = RoundedCornerShape(radius)
    val currentProgress by rememberUpdatedState(progress.coerceIn(0f, 1f))
    if (surface == GlassSurface.Chip) {
        val idle = if (selected) chipSelectionColors().first else colors.surfaceContainerHigh
        if (glass == null || (!glass.available && !native)) return background(idle, shape)
        val glassTint = if (selected) idle.copy(alpha = .9f) else
            (if (dark) lerp(colors.surfaceContainer, colors.onSurface, .04f) else colors.surfaceContainer).copy(alpha = .55f)
        val rim = (if (dark) colors.onSurface else colors.surfaceContainer).copy(alpha = if (dark) .12f else .9f)
        val chipShadow = if (selected) Modifier else Modifier.dropShadow(shape) {
            this.radius = 16.dp.toPx()
            offset = androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx())
            color = Color.Black
            alpha = .06f * currentProgress
        }
        val overlay: DrawScope.() -> Unit = {
            glassOverlay(currentProgress, native, radius, lerp(idle, glassTint, currentProgress),
                if (selected) null else rim)
        }
        return this.then(chipShadow).then(if (native) Modifier.drawBehind(overlay) else Modifier
            .drawBackdrop(glass.backdrop, shape = { shape }, effects = {
                blur((if (selected) 20.dp else 24.dp).toPx())
                colorControls(saturation = if (selected) 1.6f else 1.8f)
            }, enabled = glass.active, onDrawSurface = overlay))
    }
    if (glass == null || (!glass.available && !native)) return (if (surface == GlassSurface.Dock) this else
        shadow(2.dp, shape, clip = false,
        ambientColor = shadowColor.copy(alpha = .08f * (if (surface == GlassSurface.Button) progress else 1f)),
        spotColor = shadowColor.copy(alpha = .08f * (if (surface == GlassSurface.Button) progress else 1f))))
        .drawBehind { drawRoundRect(colors.surfaceContainer, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            alpha = if (surface == GlassSurface.Button) currentProgress else 1f) }
    val blurRadius = if (surface == GlassSurface.Dock) 28.dp else 20.dp
    val saturation = if (surface == GlassSurface.Dock) 1.8f else 1.6f
    val base = if (dark) lerp(colors.surfaceContainer, colors.onSurface, .02f) else colors.surfaceContainer
    // Native MiGlass darkens a light page slightly; the system dock stays as light as the page (+0 on white, +8 on #F2F2F2).
    val tint = base.copy(alpha = when {
        surface == GlassSurface.Button -> .62f
        dark -> .48f
        surface == GlassSurface.Dock && native -> .80f
        else -> .50f
    })
    val rim = (if (dark) colors.onSurface else colors.surfaceContainer).copy(alpha = if (dark) .10f else .95f)
    val overlay: DrawScope.() -> Unit = {
        glassOverlay(currentProgress, native, radius, tint.copy(alpha = tint.alpha * currentProgress), rim)
    }
    return (if (surface == GlassSurface.Dock) this else
        shadow(4.dp, shape, clip = false, ambientColor = shadowColor.copy(alpha = .08f * progress),
        spotColor = shadowColor.copy(alpha = .08f * progress)))
        .then(if (native) Modifier.drawBehind(overlay) else Modifier.drawBackdrop(glass.backdrop, shape = { shape }, effects = {
            blur(blurRadius.toPx())
            colorControls(saturation = saturation)
        }, enabled = glass.active, onDrawSurface = overlay))
}

private fun DrawScope.glassOverlay(progress: Float, native: Boolean, radius: Dp, tint: Color, rim: Color?) {
    if (!native) drawRect(Color.White.copy(alpha = progress), blendMode = BlendMode.DstIn)
    val corner = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
    drawRoundRect(tint, cornerRadius = corner)
    if (rim != null && !native) drawRoundRect(rim.copy(alpha = rim.alpha * progress),
        cornerRadius = corner, style = Stroke(1.dp.toPx()))
}
