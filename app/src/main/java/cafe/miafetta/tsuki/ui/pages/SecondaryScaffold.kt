package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.bottomBarInset
import cafe.miafetta.tsuki.ui.components.topBarInset
import cafe.miafetta.tsuki.ui.glass.GlassIconButton
import cafe.miafetta.tsuki.ui.glass.TopGlassFade
import cafe.miafetta.tsuki.ui.glass.TopGlassFadeInset
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
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

// Same layering as AppScaffold: scrolling content, then the top glass, then a transparent TopAppBar.
// The title doubles as the pane title; the page leaves the accessibility tree while it is not the active destination.
@Composable
internal fun SecondaryScaffold(title: String, blurEnabled: Boolean, active: Boolean, onBack: () -> Unit,
    modifier: Modifier = Modifier, backEnabled: Boolean = active, contentPadding: PaddingValues = PaddingValues(),
    spacing: Dp = 0.dp, actions: @Composable RowScope.(Float) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val behavior = MiuixScrollBehavior()
    val visible by remember(behavior) { derivedStateOf { glassVisible(behavior.state.collapsedFraction) } }
    val progress by animateGlassVisibility(visible)
    val top = topBarInset()
    val bottom = bottomBarInset()
    val collapsed = top + TopAppBarDefaults.CollapsedHeight
    val density = LocalDensity.current
    var topHeight by remember { mutableStateOf(top + 120.dp) }
    TsukiGlassHost(blurEnabled, active) {
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface), contentAlignment = Alignment.TopCenter) {
            Scaffold(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 600.dp).fillMaxSize().then(modifier).semantics {
                    paneTitle = title
                    if (!active) hideFromAccessibility()
                }) { padding ->
                // Only the content consumes the Scaffold insets; the TopAppBar still pads itself like AppScaffold's.
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().consumeWindowInsets(padding).tsukiBackdrop().background(MiuixTheme.colorScheme.surface)
                        .overScrollVertical().nestedScroll(behavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState(), overscrollEffect = null)
                        .padding(top = topHeight, bottom = bottom + 16.dp).padding(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing), content = content)
                    TsukiGlass({ glassScrollProgress(behavior.state.collapsedFraction) }, collapsed - TopGlassFadeInset,
                        Modifier.fillMaxWidth().height(collapsed + TopGlassFade))
                    TopAppBar(title, color = Color.Transparent, scrollBehavior = behavior,
                        modifier = Modifier.onSizeChanged { topHeight = with(density) { it.height.toDp() } },
                        navigationIcon = {
                            GlassIconButton(onBack, enabled = backEnabled, progress = progress) {
                                Icon(MiuixIcons.ChevronBackward, "返回")
                            }
                        }, actions = { actions(progress) })
                }
            }
        }
    }
}
