package cafe.miafetta.tsuki.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import cafe.miafetta.tsuki.BuildConfig
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.anim.folmeSpring

internal fun glassAvailable(enabled: Boolean, hardwareAccelerated: Boolean, supported: Boolean) =
    enabled && hardwareAccelerated && supported

internal fun glassVisible(collapsedFraction: Float, firstVisibleItemIndex: Int = 0): Boolean =
    collapsedFraction * 3f >= 1f || firstVisibleItemIndex > 0

internal fun glassEase(value: Float): Float {
    val t = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    return t * t * (3f - 2f * t)
}

internal fun glassScrollProgress(collapsedFraction: Float, firstVisibleItemIndex: Int = 0): Float =
    if (firstVisibleItemIndex > 0) 1f else glassEase(collapsedFraction * 3f)

@Composable
internal fun animateGlassVisibility(visible: Boolean): State<Float> = animateFloatAsState(
    if (visible) 1f else 0f,
    folmeSpring(damping = 1f, response = if (visible) .3f else .15f), label = "glass")

internal fun glassFullStrengthFraction(totalHeight: Float, fullStrengthHeight: Float): Float =
    if (totalHeight > 0f && fullStrengthHeight.isFinite()) (fullStrengthHeight / totalHeight).coerceIn(0f, 1f) else 0f

internal data class GlassContext(val backdrop: LayerBackdrop, val available: Boolean, val active: Boolean, val enabled: Boolean,
    val disabled: String? = null,
    val consumers: MutableMap<Any, Boolean> = mutableStateMapOf())
internal val LocalForceMiuixGlass = staticCompositionLocalOf { false }
internal val LocalGlass = staticCompositionLocalOf<GlassContext?> { null }

@Composable
internal fun TsukiGlassHost(enabled: Boolean, active: Boolean = true, content: @Composable () -> Unit) {
    val view = LocalView.current
    val available = remember(view, enabled) { glassAvailable(enabled, view.isHardwareAccelerated, isRuntimeShaderSupported()) }
    val backdrop = rememberLayerBackdrop()
    val consumers = remember { mutableStateMapOf<Any, Boolean>() }
    val disabled = remember {
        if (BuildConfig.DEBUG) runCatching {
            Class.forName("cafe.miafetta.tsuki.ui.GlassProbe").getField("disabled").get(null) as? String
        }.getOrNull() else null
    }
    CompositionLocalProvider(LocalGlass provides GlassContext(backdrop, available, active, enabled, disabled, consumers), content = content)
}

@Composable
internal fun Modifier.tsukiBackdrop(): Modifier {
    val glass = LocalGlass.current
    return if (glass != null && glass.available && glass.active && glass.disabled != "backdrop" &&
        glass.consumers.values.any { it }) layerBackdrop(glass.backdrop) else this
}

@Composable
internal fun nativeGlassEnabled(glass: GlassContext?): Boolean =
    glass?.enabled == true && glass.active && !(BuildConfig.DEBUG && LocalForceMiuixGlass.current) &&
        LocalView.current.isHardwareAccelerated && MiGlassCapability.supported

@Composable
internal fun ReportGlassBackend(glass: GlassContext?, backend: String) {
    val token = remember { Any() }
    DisposableEffect(glass?.consumers, backend) {
        glass?.consumers?.set(token, backend == "miuix-blur")
        onDispose { glass?.consumers?.remove(token) }
    }
}
