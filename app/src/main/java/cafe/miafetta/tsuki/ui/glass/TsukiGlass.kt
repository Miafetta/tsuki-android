package cafe.miafetta.tsuki.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import cafe.miafetta.tsuki.BuildConfig
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Blur off or unavailable: the opaque surface, unchanged. On the home page it fades out between the status bar and 8dp above
// the action row, clear of the game chips; on other pages from 32dp above the action row to the bottom of the glass.
private val OpaqueFadeInset = 32.dp
private val OpaqueGameRowInset = 8.dp

// The top bar background (docs/UI_DESIGN_CONVENTIONS.md, 玻璃范围与回退), in the order of every glass surface
// (DEVELOPMENT_CONSTRAINTS.md 6): MiGlass without tint, faded out with DstIn; miuix-blur's progressive blur without tint;
// the opaque surface. The caller's modifier sets the height, and its bottom edge is the clear line (TopGlassFade,
// GameHeaderFade). The scroll progress only scales the fade over the sampled result, never a radius.
@Composable
internal fun TsukiGlass(progress: () -> Float, statusBar: Dp, modifier: Modifier = Modifier, gameHeader: Boolean = false) {
    val glass = LocalGlass.current
    if (glass?.disabled == "topbar") return
    val configuration = LocalConfiguration.current
    var native by remember(configuration) { mutableStateOf(false) }
    val enabled = nativeGlassEnabled(glass)
    val useNative = enabled && native
    val backend = glassBackend(glass?.enabled == true, glass?.active == true, useNative, glass?.available == true)
    ReportGlassBackend(glass, backend)
    LaunchedEffect(backend) {
        if (BuildConfig.DEBUG) android.util.Log.d("TsukiGlass", "TopBar backend=$backend; MiGlass rectangle radius " +
            "$NativeTopGlassRadius, edge=1, enhancement=0, no tint; fallback progressiveBlur $TopGlassBlurRadius")
    }
    val currentProgress by rememberUpdatedState(progress)
    val nativeVisible by remember { derivedStateOf { currentProgress() > 0f } }
    var heightPx by remember { mutableIntStateOf(0) }
    val effect = when {
        useNative -> Modifier
        glass != null && glass.available -> Modifier.progressiveTop(glass, heightPx, statusBar) { currentProgress() }
        else -> Modifier.opaqueTop(heightPx, statusBar, gameHeader) { currentProgress() }
    }
    // Debug builds name the backend for the device tests (SecondaryGlassVerification); Release adds no semantics here.
    val tag = if (BuildConfig.DEBUG) Modifier.testTag("top-glass:$backend") else Modifier
    Box(modifier.onSizeChanged { heightPx = it.height }.then(tag)) {
        if (enabled) key(configuration) {
            val mask = rememberNativeTopMask(heightPx, statusBar, gameHeader)
            NativeTopGlass(mask, currentProgress, nativeVisible, NativeTopGlassRadius) { native = it }
        }
        Box(Modifier.matchParentSize().then(effect))
    }
}

// The MiGlass fade (NativeTopGlass applies it with DstIn): full strength down to the action row bottom on the home page, or
// NativeTopGlassFadeAbove above it elsewhere, then smoothstep out to the clear line.
@Composable
private fun rememberNativeTopMask(heightPx: Int, statusBar: Dp, gameHeader: Boolean): Brush {
    val density = LocalDensity.current
    return remember(heightPx, statusBar, gameHeader, density) {
        with(density) {
            val actionRow = (statusBar + TopAppBarDefaults.CollapsedHeight).toPx()
            val above = if (gameHeader) 0f else NativeTopGlassFadeAbove.toPx()
            fadeBrush(Color.White, nativeTopGlassFadeFrom(heightPx.toFloat(), actionRow, above), 1f)
        }
    }
}

// The scroll progress only scales the DstIn mask over the sampled result; the blur radius never changes with it.
@Composable
private fun Modifier.progressiveTop(glass: GlassContext, heightPx: Int, statusBar: Dp, progress: () -> Float): Modifier {
    val density = LocalDensity.current
    val curve = remember(heightPx, statusBar, density) {
        with(density) { topGlassCurve(heightPx.toFloat(), statusBar.toPx(), TopGlassPlateau.toPx(), TopGlassClearFade.toPx()) }
    }
    val gradient = remember(curve) { ProgressiveBlur(startFraction = curve.fullUntil, endFraction = 1f, curve = TopGlassBlurCurve) }
    val mask = remember(curve) { fadeBrush(Color.White, curve.fadeFrom, 1f) }
    return drawBackdrop(glass.backdrop, shape = { RectangleShape }, effects = {
        progressiveBlur(TopGlassBlurRadius.toPx(), gradient = gradient)
    }, enabled = glass.active, onDrawSurface = {
        drawRect(mask, alpha = progress().coerceIn(0f, 1f), blendMode = BlendMode.DstIn)
    })
}

@Composable
private fun Modifier.opaqueTop(heightPx: Int, statusBar: Dp, gameHeader: Boolean, progress: () -> Float): Modifier {
    val surface = MiuixTheme.colorScheme.surface
    val density = LocalDensity.current
    val tint = remember(heightPx, statusBar, gameHeader, density, surface) {
        with(density) {
            val height = heightPx.toFloat()
            val collapsed = (statusBar + TopAppBarDefaults.CollapsedHeight).toPx()
            val start = glassFullStrengthFraction(height, if (gameHeader) statusBar.toPx() else collapsed - OpaqueFadeInset.toPx())
            val end = if (gameHeader) glassFullStrengthFraction(height, collapsed - OpaqueGameRowInset.toPx()) else 1f
            fadeBrush(surface, start, end)
        }
    }
    return drawBehind { drawRect(tint, alpha = progress().coerceIn(0f, 1f)) }
}

// [color] down to [start], then smoothstep (glassEase, 33 cached stops) out to transparent at [end].
private fun fadeBrush(color: Color, start: Float, end: Float): Brush = Brush.verticalGradient(0f to color, *Array(33) { index ->
    val t = index / 32f
    start + (end - start) * t to color.copy(alpha = 1f - glassEase(t))
})

@Composable
internal fun BottomWash(modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.surface
    Box(modifier.background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface.copy(alpha = .7f)))))
}

// Background-only siblings keep text, hit targets and dock animation outside the native View.
@Composable
internal fun GlassBackground(surface: GlassSurface, modifier: Modifier, progress: Float = 1f, selected: Boolean = false) {
    val glass = LocalGlass.current
    if (glass?.disabled == surface.name.lowercase()) return
    val configuration = LocalConfiguration.current
    var native by remember(configuration) { mutableStateOf(false) }
    val enabled = nativeGlassEnabled(glass)
    val radius = surfaceBlurRadius(surface, selected)
    val useNative = enabled && native
    val backend = glassBackend(glass?.enabled == true, glass?.active == true, useNative, glass?.available == true)
    ReportGlassBackend(glass, backend)
    LaunchedEffect(backend) {
        if (BuildConfig.DEBUG) android.util.Log.d("TsukiGlass",
            "$surface backend=$backend (native calls accepted; visual acceptance separate)")
    }
    Box(modifier) {
        if (enabled) key(configuration) {
            AndroidView(modifier = Modifier.matchParentSize(), factory = { MiGlassView(it) },
                update = { view ->
                    view.report = { native = it }
                    view.configure(radius, true, progress)
                },
                onRelease = { it.release() })
        }
        Box(Modifier.matchParentSize().tsukiGlass(surface, progress, selected, useNative))
    }
}

@Composable
internal fun GlassIconButton(onClick: () -> Unit, progress: Float, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(modifier) {
        GlassBackground(GlassSurface.Button, Modifier.matchParentSize(), progress)
        IconButton(onClick, enabled = enabled, content = content)
    }
}
