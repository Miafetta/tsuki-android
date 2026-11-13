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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TsukiGlass(progress: () -> Float, fullStrengthHeight: Dp, modifier: Modifier = Modifier, gameHeader: Boolean = false) {
    val glass = LocalGlass.current
    if (glass?.disabled == "topbar") return
    val configuration = LocalConfiguration.current
    var native by remember(configuration) { mutableStateOf(false) }
    val enabled = nativeGlassEnabled(glass)
    val useNative = enabled && native
    val backend = glassBackend(glass?.enabled == true, glass?.active == true, useNative, glass?.available == true)
    ReportGlassBackend(glass, backend)
    LaunchedEffect(backend) {
        if (BuildConfig.DEBUG) android.util.Log.d("TsukiGlass", "TopBar backend=$backend; rectangle, edge=1, enhancement=0")
    }
    val surface = MiuixTheme.colorScheme.surface
    val currentProgress by rememberUpdatedState(progress)
    val nativeVisible by remember { derivedStateOf { currentProgress() > 0f } }
    var heightPx by remember { mutableIntStateOf(0) }
    val fullPx = with(LocalDensity.current) { fullStrengthHeight.toPx() }
    val boundary = remember(heightPx, fullPx) { glassFullStrengthFraction(heightPx.toFloat(), fullPx) }
    val gradient = remember(boundary, gameHeader) {
        ProgressiveBlur(startFraction = if (gameHeader) .4f else boundary, endFraction = 1f, curve = if (gameHeader) 1.4f else .65f)
    }
    val maskBoundary = if (gameHeader) glassFullStrengthFraction(heightPx.toFloat(),
        fullPx + with(LocalDensity.current) { 20.dp.toPx() }) else boundary
    val mask = remember(maskBoundary) {
        Brush.verticalGradient(0f to Color.White, *Array(33) { index ->
            val t = index / 32f
            maskBoundary + (1f - maskBoundary) * t to Color.White.copy(alpha = 1f - glassEase(t))
        })
    }
    val tintStart = if (gameHeader) glassFullStrengthFraction(heightPx.toFloat(),
        fullPx - with(LocalDensity.current) { 52.dp.toPx() }) else boundary
    val tintEnd = if (gameHeader) glassFullStrengthFraction(heightPx.toFloat(),
        fullPx - with(LocalDensity.current) { 8.dp.toPx() }) else 1f
    val tint = remember(tintStart, tintEnd, surface) {
        Brush.verticalGradient(0f to surface, *Array(33) { index ->
            val t = index / 32f
            tintStart + (tintEnd - tintStart) * t to surface.copy(alpha = 1f - glassEase(t))
        })
    }
    val effect = if (useNative) Modifier.drawBehind {
        drawRect(tint, alpha = .34f * currentProgress().coerceIn(0f, 1f))
    } else if (glass != null && glass.available) Modifier.drawBackdrop(glass.backdrop,
        shape = { RectangleShape }, effects = {
            progressiveBlur((if (gameHeader) 10.dp else 30.dp).toPx(), gradient = gradient)
        },
        // Use the continuous-radius path
        // our mask already restores the sharp source at the end.
        enabled = glass.active, onDrawSurface = {
            drawRect(mask, alpha = currentProgress().coerceIn(0f, 1f), blendMode = BlendMode.DstIn)
            drawRect(tint, alpha = .34f * currentProgress().coerceIn(0f, 1f))
        }) else Modifier.drawBehind {
            drawRect(tint, alpha = currentProgress().coerceIn(0f, 1f))
        }
    Box(modifier.onSizeChanged { heightPx = it.height }) {
        if (enabled) key(configuration) {
            NativeTopGlass(mask, currentProgress, nativeVisible) { native = it }
        }
        Box(Modifier.matchParentSize().then(effect))
    }
}

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
    val radius = when (surface) {
        // Matches the blur measured on the HyperOS 4 Phone app dock (2026-11-13): sigma about 25 px.
        GlassSurface.Dock -> 14f
        GlassSurface.Button -> 20f
        GlassSurface.Chip -> if (selected) 20f else 24f
    }
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
