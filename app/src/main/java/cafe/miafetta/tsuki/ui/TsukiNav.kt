package cafe.miafetta.tsuki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled

// Covered layers keep alpha = 1 so they do not wrap in an offscreen RenderNode (NavDisplay's dim scrim handles darkening).
private const val COVERED_PARALLAX_FRACTION = 0.25f

// Snap entering/leaving translation to whole pixels to prevent subpixel shimmer on corner-clipped edges.
internal fun navSlideTranslationX(relativeDepth: Float, width: Float, rtl: Boolean): Float {
    if (!relativeDepth.isFinite() || !width.isFinite()) return 0f
    return ((if (rtl) -1f else 1f) * (-relativeDepth).coerceIn(0f, 1f) * width).roundToInt().toFloat()
}

internal fun navCoveredTranslationX(relativeDepth: Float, width: Float, rtl: Boolean): Float {
    if (!relativeDepth.isFinite() || !width.isFinite()) return 0f
    return (if (rtl) 1f else -1f) * relativeDepth.coerceIn(0f, 1f) * width * COVERED_PARALLAX_FRACTION
}

// Mirrors NavTransitions.MiuixDefault motion while keeping alpha = 1 on covered pages so MiGlassView blurs its own page.
internal fun tsukiNavTransition(): NavTransition = navGraphicsTransition(opaqueDepth = 1f) { scope ->
    val width = scope.layoutSize.width.toFloat()
    val depth = scope.relativeDepth
    val rtl = scope.layoutDirection == LayoutDirection.Rtl
    translationX = if (depth <= 0f) navSlideTranslationX(depth, width, rtl)
    else navCoveredTranslationX(depth, width, rtl)
}

// Secondary pages slide in inside one window; clip the entering page's leading corners to the screen corner radius.
@Composable
internal fun tsukiNavEffects() = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius(),
    blockInputDuringTransition = true)

// Disables squircle masks strictly on the NavDisplay entry clip (which would wrap transitioning pages in contentLayer /
// saveLayer offscreen buffers and break HyperOS MiGlassView sampling), falling back to hardware rounded-corner outline
// clipping. Individual pages restore squircles immediately via TsukiNavEntry.
@Composable
internal fun TsukiNavHost(navController: NavController, onBack: () -> Unit, modifier: Modifier = Modifier,
    content: NavEntryBuilder.() -> Unit) {
    CompositionLocalProvider(LocalSquircleEnabled provides false) {
        NavDisplay(navController = navController, modifier = modifier, onBack = onBack,
            transition = tsukiNavTransition(), effects = tsukiNavEffects(), content = content)
    }
}

@Composable
internal fun TsukiNavEntry(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSquircleEnabled provides true, content = content)
}
