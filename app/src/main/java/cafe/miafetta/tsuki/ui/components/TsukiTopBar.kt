package cafe.miafetta.tsuki.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Miuix TopAppBarLayout tweens its title colours over 50 ms; the drawn titles follow theme changes the same way.
private const val TitleColorMillis = 50
// Miuix TITLE_WIDTH_FRACTION: the small title gets this share of the width left by the navigation icon and actions.
private const val TitleWidthFraction = .9
// Transparent margin around each blurred title layer (TitleMotion.BlurSpread).
private val TitleBleed = titleBlurMarginDp().dp
private val LargeTitleDrop = TitleMotion.LargeTitleDropDp.dp

// Where the pages below a TsukiTopBar start their content and put their back button (docs/UI_DESIGN_CONVENTIONS.md C1).
// Measured on the HyperOS 4 Settings pages "移动网络" and the Settings home page (2026-11-16, 1200 x 2670 px, 3.25 px/dp,
// status bar 140 px).
internal object TopBarSpacing {
    // On both pages the straight top edge of the first content (a card, the search box) is at row 509, 113.5dp below the
    // status bar; the expanded TsukiTopBar ends 95.4dp below it (row 450, at font scale 1). The content keeps the
    // difference below the bar as its own top padding instead of a taller bar, so the titles and the glass keep their
    // relation to the collapsed distance.
    const val ContentGapDp = 18f
    // The system's back button ink spans columns 86..120; with Miuix's 16dp navigation icon padding Tsuki's spanned 99..134,
    // 13.5 px (4.15dp) further right. Only the navigation icon moves; the actions keep Miuix's ActionIconPadding.
    const val BackButtonShiftDp = 4f
}

internal val TopBarContentGap = TopBarSpacing.ContentGapDp.dp
// Start padding of the secondary pages' back button: Miuix's NavigationIconPadding less the shift.
internal val BackButtonPadding = TopAppBarDefaults.NavigationIconPadding - TopBarSpacing.BackButtonShiftDp.dp

/** Top of a page's first content below a TsukiTopBar that measures [barHeight], status bar included. */
internal fun topBarContentTop(barHeight: Dp): Dp = barHeight + TopBarContentGap

/**
 * The collapsing top bar of the main pages and the scrolling secondary pages (docs/UI_DESIGN_CONVENTIONS.md C1).
 * Miuix [TopAppBar] keeps the layout, nested scrolling, height and heightOffsetLimit; its two titles stay, transparent,
 * because they size the bar and carry the accessibility text. Tsuki draws both titles above it in the style Miuix uses,
 * with the HyperOS 4 hand-over of [TitleMotion]: the small title on Miuix's pixels, the large title
 * [TitleMotion.LargeTitleDropDp] lower, where the system puts it. The drawn copies have no semantics and no pointer
 * input, so taps still reach the bar. [navigationIconPadding] is Miuix's start padding of the icon; the drawn small title
 * keeps clear of the icon with the same padding Miuix uses.
 */
@Composable
internal fun TsukiTopBar(title: String, scrollBehavior: ScrollBehavior, modifier: Modifier = Modifier,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    val slots = remember { TitleSlots() }
    Box {
        // The wrappers only record widths. A plain Box around the icon, and a Row arranged like Miuix's own around the
        // actions, measure and place their content exactly as Miuix's slots would without them.
        TopAppBar(title = title, modifier = modifier, color = Color.Transparent, titleColor = Color.Transparent,
            largeTitleColor = Color.Transparent, scrollBehavior = scrollBehavior,
            navigationIconPadding = navigationIconPadding,
            navigationIcon = { Box(Modifier.onSizeChanged { slots.navigation = it.width }) { navigationIcon() } },
            actions = {
                Row(Modifier.onSizeChanged { slots.actions = it.width }, Arrangement.End, Alignment.CenterVertically) {
                    actions()
                }
            })
        TitleOverlay(title, scrollBehavior, slots, navigationIconPadding, Modifier.matchParentSize())
    }
}

// Widths of the caller's navigation icon and action row without Miuix's paddings. Miuix measures them before the Box
// measures the overlay, in the same pass, so the overlay always sees the current values.
@Stable
private class TitleSlots {
    var navigation by mutableIntStateOf(0)
    var actions by mutableIntStateOf(0)
}

@Composable
private fun TitleOverlay(title: String, behavior: ScrollBehavior, slots: TitleSlots, navigationPadding: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val collapsedDp = remember(behavior, density) { { -behavior.state.heightOffset / density.density } }
    // A bar restored in its collapsed state shows the small title at once. The offset is only read in layout, drawing
    // and the effect below, so scrolling never recomposes the titles.
    val progress = remember(behavior) {
        Animatable(if (Snapshot.withoutReadObservation { smallTitleVisible(collapsedDp()) }) 1f else 0f)
    }
    LaunchedEffect(behavior, collapsedDp) {
        snapshotFlow { smallTitleVisible(collapsedDp()) }.collectLatest { visible ->
            val response = if (visible) TitleMotion.SmallTitleShowResponse else TitleMotion.SmallTitleHideResponse
            progress.animateTo(if (visible) 1f else 0f, folmeSpring(damping = 1f, response = response))
        }
    }
    val color by animateColorAsState(MiuixTheme.colorScheme.onSurface, tween(TitleColorMillis))
    Layout(content = {
        LargeTitle(title, color, collapsedDp)
        SmallTitle(title, color) { progress.value }
    }, modifier = modifier
        // The same insets, in the same order, as Miuix TopAppBarLayout, and its clip.
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        .clipToBounds()) { measurables, constraints ->
        placeTitles(measurables, constraints, slots, navigationPadding) { behavior.state.heightOffset }
    }
}

// Puts the titles where Miuix TopAppBarLayout puts its own, except for the large title's fixed drop: the large title at
// the title padding, the collapsed height plus LargeTitleDrop down, moved 1:1 by the height offset; the small title
// centred on the collapsed bar within the width the navigation icon and actions leave, pushed clear of them. Each layer is
// TitleBleed larger on every side, so measured and placed that much larger and earlier, its text gets Miuix's constraints
// (and the small title Miuix's pixels).
private fun MeasureScope.placeTitles(measurables: List<Measurable>, constraints: Constraints, slots: TitleSlots,
    navigationPadding: Dp, heightOffset: () -> Float): MeasureResult {
    val bleed = TitleBleed.roundToPx()
    val drop = LargeTitleDrop.roundToPx()
    val pad = TopAppBarDefaults.TitlePadding.roundToPx()
    val collapsed = TopAppBarDefaults.CollapsedHeight.roundToPx()
    val width = constraints.maxWidth
    val large = measurables[0].measure(Constraints(maxWidth = (width - 2 * pad).coerceAtLeast(0) + 2 * bleed))
    val navigation = (slots.navigation + navigationPadding.roundToPx()).coerceAtMost(width)
    val actions = (slots.actions + TopAppBarDefaults.ActionIconPadding.roundToPx()).coerceAtMost(width)
    // Math.round is Miuix's fastRoundToInt.
    val boxMax = Math.round((width - navigation - actions).coerceAtLeast(0) * TitleWidthFraction).toInt()
    val small = measurables[1].measure(Constraints(maxWidth = (boxMax - 2 * pad).coerceAtLeast(0) + 2 * bleed))
    val box = (small.width - 2 * bleed + 2 * pad).coerceAtMost(boxMax)
    val boxHeight = small.height - 2 * bleed
    var x = (width - box) / 2
    if (x < navigation) x = navigation
    else if (x + box > width - actions) x = width - actions - box
    return layout(width, constraints.maxHeight) {
        large.placeRelative(pad - bleed, largeTitleTop(collapsed, drop, bleed, heightOffset()))
        small.placeRelative(x + pad - bleed, collapsed / 2 - boxHeight / 2 - bleed)
    }
}

@Composable
private fun LargeTitle(title: String, color: Color, collapsedDp: () -> Float) {
    Box(Modifier.graphicsLayer {
        val distance = collapsedDp()
        alpha = largeTitleAlpha(distance)
        titleBlur(largeTitleBlurSigma(distance))
    }.padding(TitleBleed)) {
        // Miuix TopAppBarLayout's large title: title1 size, normal weight, wrapping.
        Text(title, Modifier.clearAndSetSemantics {}, color = color, fontSize = MiuixTheme.textStyles.title1.fontSize,
            fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun SmallTitle(title: String, color: Color, progress: () -> Float) {
    Box(Modifier.graphicsLayer {
        val p = progress()
        alpha = smallTitleAlpha(p)
        translationY = smallTitleShift(p).dp.toPx()
        titleBlur(smallTitleBlurSigma(p))
    }.padding(TitleBleed)) {
        // Miuix TopAppBarLayout's small title: title3 size, medium weight, one line with an ellipsis.
        Text(title, Modifier.clearAndSetSemantics {}, color = color, fontSize = MiuixTheme.textStyles.title3.fontSize,
            fontWeight = FontWeight.Medium, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

// Same construction as the menu content blur (TsukiMenu); none at all while the layer rests (titleBlurRadius).
private fun GraphicsLayerScope.titleBlur(sigma: Float) {
    val radius = titleBlurRadius(sigma.dp.toPx(), alpha)
    renderEffect = if (radius > 0f) android.graphics.RenderEffect.createBlurEffect(radius, radius,
        android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect() else null
}
