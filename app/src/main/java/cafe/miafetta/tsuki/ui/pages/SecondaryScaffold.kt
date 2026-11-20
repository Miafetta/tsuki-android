package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.BackButtonPadding
import cafe.miafetta.tsuki.ui.components.ScrollToTopEffect
import cafe.miafetta.tsuki.ui.components.TsukiTopBar
import cafe.miafetta.tsuki.ui.components.bottomBarInset
import cafe.miafetta.tsuki.ui.components.expandTopBar
import cafe.miafetta.tsuki.ui.components.topBarContentTop
import cafe.miafetta.tsuki.ui.components.topBarInset
import cafe.miafetta.tsuki.ui.glass.GlassIconButton
import cafe.miafetta.tsuki.ui.glass.TopGlassFade
import cafe.miafetta.tsuki.ui.glass.TransitionGlass
import cafe.miafetta.tsuki.ui.glass.TsukiGlass
import cafe.miafetta.tsuki.ui.glass.TsukiGlassHost
import cafe.miafetta.tsuki.ui.glass.animateGlassVisibility
import cafe.miafetta.tsuki.ui.glass.glassScrollProgress
import cafe.miafetta.tsuki.ui.glass.glassVisible
import cafe.miafetta.tsuki.ui.glass.tsukiBackdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

// Same layering as AppScaffold: scrolling content, then the top glass, then a transparent TopAppBar.
// The title doubles as the pane title; the page leaves the accessibility tree while it is not the active destination.
// scrollable = false is the compact variant (UI_DESIGN_CONVENTIONS C13) for content that scrolls itself, such as a
// WebView: a SmallTopAppBar above a fixed-height body, without top glass or backdrop recording.
// backEnabled only reflects real busy states. During the slide-in the button keeps its normal look: NavDisplay blocks
// input then, and a click is ignored until the page is active. The back button sits BackButtonPadding from the start in
// both variants, where the system puts its own (TopBarSpacing); the actions keep Miuix's padding.
@Composable
internal fun SecondaryScaffold(title: String, blurEnabled: Boolean, active: Boolean, onBack: () -> Unit,
    modifier: Modifier = Modifier, backEnabled: Boolean = true, contentPadding: PaddingValues = PaddingValues(),
    spacing: Dp = 0.dp, scrollable: Boolean = true, actions: @Composable RowScope.(Float) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit) {
    // The glass follows the page's transition (TransitionGlass), not [active], which only gates input.
    val transition = LocalNavTransitionScope.current
    TsukiGlassHost(blurEnabled, TransitionGlass.active(transition.isRunning, transition.relativeDepth)) {
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface), contentAlignment = Alignment.TopCenter) {
            Scaffold(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 600.dp).fillMaxSize().then(modifier).semantics {
                    paneTitle = title
                    if (!active) hideFromAccessibility()
                }) { padding ->
                val onActiveBack = { if (active) onBack() }
                val body = SecondaryBody(title, padding, active, onActiveBack, backEnabled, contentPadding, spacing, actions,
                    content)
                if (scrollable) ScrollingBody(body) else FixedBody(body)
            }
        }
    }
}

private class SecondaryBody(val title: String, val padding: PaddingValues, val active: Boolean, val onBack: () -> Unit,
    val backEnabled: Boolean, val contentPadding: PaddingValues, val spacing: Dp,
    val actions: @Composable RowScope.(Float) -> Unit, val content: @Composable ColumnScope.() -> Unit)

@Composable
private fun BackButton(body: SecondaryBody, progress: Float) {
    GlassIconButton(body.onBack, enabled = body.backEnabled, progress = progress) {
        Icon(MiuixIcons.ChevronBackward, "返回")
    }
}

@Composable
private fun ScrollingBody(body: SecondaryBody) {
    val behavior = MiuixScrollBehavior()
    val visible by remember(behavior) { derivedStateOf { glassVisible(behavior.state.collapsedFraction) } }
    val progress by animateGlassVisibility(visible)
    val top = topBarInset()
    val bottom = bottomBarInset()
    val collapsed = top + TopAppBarDefaults.CollapsedHeight
    val density = LocalDensity.current
    var topHeight by remember { mutableStateOf(top + 120.dp) }
    val scroll = rememberScrollState()
    // HyperOS 4 status bar tap while this page is the active one (the compact variant leaves it to its own scroller).
    ScrollToTopEffect(body.active, canScrollUp = { scroll.value > 0 || behavior.state.heightOffset < 0f }) {
        scroll.animateScrollTo(0)
        expandTopBar(behavior)
    }
    // Only the content consumes the Scaffold insets; the TopAppBar still pads itself like AppScaffold's. The first content
    // starts TopBarContentGap below the bar, as on the main pages.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().consumeWindowInsets(body.padding).tsukiBackdrop()
            .background(MiuixTheme.colorScheme.surface)
            .overScrollVertical().nestedScroll(behavior.nestedScrollConnection)
            .verticalScroll(scroll, overscrollEffect = null)
            .padding(top = topBarContentTop(topHeight), bottom = bottom + 16.dp).padding(body.contentPadding),
            verticalArrangement = Arrangement.spacedBy(body.spacing), content = body.content)
        TsukiGlass({ glassScrollProgress(behavior.state.collapsedFraction) }, top,
            Modifier.fillMaxWidth().height(collapsed + TopGlassFade))
        TsukiTopBar(body.title, behavior,
            modifier = Modifier.onSizeChanged { topHeight = with(density) { it.height.toDp() } },
            navigationIconPadding = BackButtonPadding,
            navigationIcon = { BackButton(body, progress) }, actions = { body.actions(this, progress) })
    }
}

// The body fills the space between the bar and the navigation bar; nothing scrolls under the bar. As in
// ScrollingBody only the content consumes the Scaffold insets: the bar pads itself for the status bar. Without a large
// title there is no TopBarContentGap: the content (a WebView) starts right below the small bar (C13).
@Composable
private fun FixedBody(body: SecondaryBody) {
    val bottom = bottomBarInset()
    Column(Modifier.fillMaxSize()) {
        SmallTopAppBar(body.title, color = MiuixTheme.colorScheme.surface, navigationIconPadding = BackButtonPadding,
            navigationIcon = { BackButton(body, 0f) }, actions = { body.actions(this, 0f) })
        Column(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(body.padding).padding(bottom = bottom)
            .padding(body.contentPadding), verticalArrangement = Arrangement.spacedBy(body.spacing), content = body.content)
    }
}
