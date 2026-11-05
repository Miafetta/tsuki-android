package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.state.AppPage
import cafe.miafetta.tsuki.ui.theme.Branding

import cafe.miafetta.tsuki.ui.components.GameTabs
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.components.bottomBarInset
import cafe.miafetta.tsuki.ui.components.topBarInset
import cafe.miafetta.tsuki.ui.glass.BottomWash
import cafe.miafetta.tsuki.ui.glass.GameHeaderFade
import cafe.miafetta.tsuki.ui.glass.GameChipSlot
import cafe.miafetta.tsuki.ui.glass.GlassIconButton
import cafe.miafetta.tsuki.ui.glass.TopGlassFade
import cafe.miafetta.tsuki.ui.glass.TopGlassFadeInset
import cafe.miafetta.tsuki.ui.glass.TsukiGlass
import cafe.miafetta.tsuki.ui.glass.TsukiGlassHost
import cafe.miafetta.tsuki.ui.glass.animateGlassVisibility
import cafe.miafetta.tsuki.ui.glass.glassScrollProgress
import cafe.miafetta.tsuki.ui.glass.glassVisible
import cafe.miafetta.tsuki.ui.glass.tsukiBackdrop

import androidx.compose.animation.core.animate
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.*

@Composable
internal fun AppScaffold(page: Int, modifier: Modifier = Modifier, canRefresh: Boolean = false,
    showRefresh: Boolean = canRefresh, onRefresh: () -> Unit = {},
    onNavigate: (Int) -> Unit = {}, onAdd: () -> Unit = {}, addButtonVisible: Boolean = true,
    blurEnabled: Boolean = true, glassActive: Boolean = true, refreshing: Boolean = false,
    appearanceIndex: Int? = null, scrollToAppearance: Boolean = false,
    games: List<Pair<String, String>> = listOf(Branding.GAME_ID to Branding.GAME), selectedGameId: String = Branding.GAME_ID,
    onGameSelect: (String) -> Unit = {}, overlays: @Composable () -> Unit = {}, content: LazyListScope.(Int) -> Unit) {
    val currentPage by rememberUpdatedState(page)
    var destination by rememberSaveable { mutableIntStateOf(page.coerceIn(AppPage.OVERVIEW, AppPage.ME)) }
    val navigate by rememberUpdatedState(onNavigate)
    val pager = rememberPagerState(initialPage = page.coerceIn(AppPage.OVERVIEW, AppPage.ME)) { 3 }
    val lists = List(3) { key(it) { rememberLazyListState() } }
    val behaviors = List(3) { key(it) { MiuixScrollBehavior() } }
    val scope = rememberCoroutineScope()
    var returningToTop by remember { mutableStateOf(false) }
    val refreshAllowed by rememberUpdatedState(canRefresh)
    val refresh by rememberUpdatedState(onRefresh)
    val refreshFromTop: () -> Unit = {
        if (refreshAllowed && !returningToTop) {
            returningToTop = true
            scope.launch {
                try {
                    lists[AppPage.OVERVIEW].animateScrollToItem(0)
                    val header = behaviors[AppPage.OVERVIEW].state
                    animate(header.heightOffset, 0f,
                        animationSpec = behaviors[AppPage.OVERVIEW].snapAnimationSpec ?: spring()) { value, _ ->
                        header.heightOffset = value
                    }
                    header.contentOffset = 0f
                    if (refreshAllowed && pager.currentPage == AppPage.OVERVIEW) refresh()
                } finally { returningToTop = false }
            }
        }
    }
    var appearanceHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scrollToAppearance, appearanceIndex) {
        if (scrollToAppearance && !appearanceHandled && appearanceIndex != null) {
            lists[2].scrollToItem(appearanceIndex)
            appearanceHandled = true
        }
    }
    LaunchedEffect(page) { destination = page.coerceIn(AppPage.OVERVIEW, AppPage.ME) }
    // A horizontal swipe, or a touch that interrupts the dock's page animation, settles the pager without the dock.
    // Adopt the settled page so the dock, the app page and the top action (which requires both to agree) stay in sync.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { settled ->
            destination = settled
            if (currentPage != settled) navigate(settled)
        }
    }
    LaunchedEffect(destination) {
        // A new destination cancels the previous scroll before continuing from its current offset.
        if (pager.currentPage != destination || pager.currentPageOffsetFraction != 0f)
            pager.springAnimateToPage(destination)
        if (currentPage != destination) navigate(destination)
    }
    TsukiGlassHost(blurEnabled, glassActive) {
        AppScaffoldLayout(pager, lists, behaviors, destination, { destination = it }, currentPage,
            modifier, canRefresh && !returningToTop, showRefresh, onRefresh, refreshFromTop, onAdd, addButtonVisible, refreshing,
            games, selectedGameId, onGameSelect, overlays, content)
    }
}

@Composable
private fun AppScaffoldLayout(pager: PagerState, lists: List<LazyListState>, behaviors: List<ScrollBehavior>,
    destination: Int, onDestination: (Int) -> Unit, currentPage: Int, modifier: Modifier,
    canRefresh: Boolean, showRefresh: Boolean, onRefresh: () -> Unit, refreshFromTop: () -> Unit,
    onAdd: () -> Unit, addButtonVisible: Boolean,
    refreshing: Boolean, games: List<Pair<String, String>>, selectedGameId: String, onGameSelect: (String) -> Unit,
    overlays: @Composable () -> Unit, content: LazyListScope.(Int) -> Unit) {
    val active = pager.currentPage
    val density = LocalDensity.current
    val status = topBarInset()
    val navBottom = bottomBarInset()
    var topHeight by remember { mutableStateOf(status + 120.dp) }
    val collapsed = status + TopAppBarDefaults.CollapsedHeight
    val activeList = lists[active]
    val activeBehavior = behaviors[active]
    val visible by remember(activeList, activeBehavior) { derivedStateOf {
        glassVisible(activeBehavior.state.collapsedFraction, activeList.firstVisibleItemIndex)
    } }
    val progress by animateGlassVisibility(visible)
    val overviewList = lists[0]
    val chipTop by remember(overviewList, density, topHeight, collapsed) { derivedStateOf {
        if (overviewList.firstVisibleItemIndex > 0) collapsed else
            (topHeight - with(density) { overviewList.firstVisibleItemScrollOffset.toDp() }).coerceAtLeast(collapsed)
    } }
    Box(modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface), contentAlignment = Alignment.TopCenter) {
        Scaffold(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .widthIn(max = 600.dp).fillMaxSize()) { padding ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().tsukiBackdrop().consumeWindowInsets(padding)) {
                AppPager(pager, lists, behaviors, topHeight, navBottom, refreshing, canRefresh, onRefresh, content)
                }
                TsukiGlass({ glassScrollProgress(activeBehavior.state.collapsedFraction, activeList.firstVisibleItemIndex) },
                    collapsed - if (active == AppPage.OVERVIEW) 0.dp else TopGlassFadeInset, Modifier.fillMaxWidth()
                        .height(collapsed + if (active == AppPage.OVERVIEW) GameHeaderFade else TopGlassFade),
                    gameHeader = active == AppPage.OVERVIEW)
                TopAppBar(title = listOf("概览", "部件", "我的")[active], color = Color.Transparent,
                    modifier = Modifier.onSizeChanged { topHeight = with(density) { it.height.toDp() } },
                        scrollBehavior = behaviors[active],
                    actions = {
                        AppTopAction(active, showRefresh, addButtonVisible, canRefresh, progress, refreshFromTop) {
                            if (!pager.isScrollInProgress && active == currentPage) onAdd()
                        }
                    })
                ScaffoldGameTabs(active, activeList, activeBehavior, chipTop, games, selectedGameId, onGameSelect)
                BottomWash(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navBottom + 92.dp))
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    TsukiNavDock(destination, onDestination)
                }
                overlays()
            }
        }
    }
}

@Composable
private fun AppPager(pager: PagerState, lists: List<LazyListState>, behaviors: List<ScrollBehavior>,
    topHeight: Dp, navBottom: Dp, refreshing: Boolean, canRefresh: Boolean, onRefresh: () -> Unit,
    content: LazyListScope.(Int) -> Unit) {
    HorizontalPager(pager, modifier = Modifier.fillMaxSize().pagerGestureOverride(pager), userScrollEnabled = false,
        pageNestedScrollConnection = PagerGestureNestedScrollConnection, beyondViewportPageCount = 1) { index ->
        @Composable fun ListContent() {
            LazyColumn(Modifier.fillMaxSize().testTag("page-list:$index")
                .overScrollVertical().nestedScroll(behaviors[index].nestedScrollConnection),
                state = lists[index], overscrollEffect = null,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                    top = topHeight + (if (index == AppPage.OVERVIEW) 0.dp else 8.dp), bottom = navBottom + 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (index == AppPage.OVERVIEW) item("game-chips-slot") { Spacer(Modifier.height(GameChipSlot)) }
                content(index)
            }
        }
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        if (index == AppPage.OVERVIEW) PullToRefresh(isRefreshing = refreshing, onRefresh = { if (canRefresh) onRefresh() },
            topAppBarScrollBehavior = behaviors[0], contentPadding = PaddingValues(top = topHeight + GameChipSlot),
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功")) { ListContent() }
        else ListContent()
        }
    }
}

@Composable
private fun AppTopAction(active: Int, showRefresh: Boolean, addButtonVisible: Boolean, canRefresh: Boolean,
    progress: Float, onRefresh: () -> Unit, onAdd: () -> Unit) {
    val overview = active == AppPage.OVERVIEW
    val shown = if (overview) showRefresh else addButtonVisible
    GlassIconButton(onClick = {
        if (overview) onRefresh()
        else onAdd()
    }, enabled = shown && (!overview || canRefresh), progress = if (shown) progress else 0f,
        modifier = Modifier.graphicsLayer { alpha = if (shown) 1f else 0f }
            .then(if (!shown) Modifier.clearAndSetSemantics {} else if (overview)
                Modifier.testTag("top-refresh") else Modifier)) {
        Icon(if (overview) MiuixIcons.Refresh else MiuixIcons.Add, if (overview) "刷新" else "添加")
    }
}

private const val OffscreenChipOffset = 100_000

@Composable
private fun ScaffoldGameTabs(active: Int, activeList: LazyListState, activeBehavior: ScrollBehavior,
    chipTop: Dp, games: List<Pair<String, String>>, selectedGameId: String, onGameSelect: (String) -> Unit) {
    val overview = active == AppPage.OVERVIEW
    // Stay composed so native chip glass is reused, but leave the window on other pages: an invisible overlay would still
    // win hit testing and swallow taps on the content below it (for example the Widgets tab row).
    GameTabs(games, selectedGameId, onGameSelect,
        Modifier.offset { androidx.compose.ui.unit.IntOffset(0, if (overview) chipTop.roundToPx() else -OffscreenChipOffset) }
            .graphicsLayer { alpha = if (overview) 1f else 0f }
            .then(if (overview) Modifier else Modifier.clearAndSetSemantics {}),
        enabled = overview) {
        if (overview) glassScrollProgress(activeBehavior.state.collapsedFraction, activeList.firstVisibleItemIndex) else 0f
    }

}
