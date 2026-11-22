package cafe.miafetta.tsuki.ui.sheets

import cafe.miafetta.tsuki.ui.components.EmptyCard
import cafe.miafetta.tsuki.ui.components.MutedText
import cafe.miafetta.tsuki.ui.components.PreviewTarget
import cafe.miafetta.tsuki.ui.components.TsukiDropdownPreference
import cafe.miafetta.tsuki.ui.components.WidgetPagerMotion
import cafe.miafetta.tsuki.ui.components.WidgetPreviewPager
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.components.WidgetSheet
import cafe.miafetta.tsuki.ui.components.WidgetSheetHeader
import cafe.miafetta.tsuki.ui.components.WidgetSheetInset
import cafe.miafetta.tsuki.ui.components.crossGameAlpha
import cafe.miafetta.tsuki.ui.components.pageSettingsAlpha
import cafe.miafetta.tsuki.ui.components.widgetCatalogIndex
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.state.UiWidget
import cafe.miafetta.tsuki.ui.state.widgetFeature
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.ui.theme.widgetCatalog
import cafe.miafetta.tsuki.ui.theme.widgetDescription
import cafe.miafetta.tsuki.ui.theme.widgetName
import cafe.miafetta.tsuki.ui.theme.widgetThemes
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter

import android.app.Activity
import android.appwidget.AppWidgetManager
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.widget.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

// A sheet adds a new widget only when no instance exists yet. A placed instance, configured or not (the launcher's
// configure step, a tap on an unconfigured widget), is edited: saving applies to it instead of pinning another.
internal fun isNewWidget(current: UiWidget?, widgetId: Int) = current == null && widgetId <= AppWidgetManager.INVALID_APPWIDGET_ID

@Composable
internal fun ConfigEditor(activity: Activity, roles: List<UiRole>, current: UiWidget?, busy: Boolean, error: String,
    onDismiss: () -> Unit, onAccounts: () -> Unit, onSave: (UiRole, String, Boolean) -> Unit,
    previews: WidgetPreviews, previewSnapshot: suspend (UiRole) -> WidgetSnapshot?,
    show: Boolean = true, onDismissFinished: (() -> Unit)? = null, showTools: Boolean = false,
    widgetId: Int = -1, initialTheme: String = "LIGHT", overviewPreview: Pair<String, WidgetSnapshot?>? = null,
    game: String = Branding.GAME_ID, initialRoleKeys: Map<String, String> = emptyMap(),
    initialSnapshots: Map<String, Pair<String, WidgetSnapshot?>> = emptyMap()) {
    if (isNewWidget(current, widgetId) && !showTools) {
        ConfigEditorPager(roles, busy, error, onDismiss, onAccounts, onSave, previews, previewSnapshot,
            show, onDismissFinished, initialTheme, overviewPreview, game, initialRoleKeys, initialSnapshots)
    } else {
        ConfigEditorSingle(activity, roles, current, busy, error, onDismiss, onAccounts, onSave, previews,
            previewSnapshot, show, onDismissFinished, showTools, widgetId, initialTheme, overviewPreview, game)
    }
}

@Composable
private fun ConfigEditorPager(roles: List<UiRole>, busy: Boolean, error: String, onDismiss: () -> Unit,
    onAccounts: () -> Unit, onSave: (UiRole, String, Boolean) -> Unit, previews: WidgetPreviews,
    previewSnapshot: suspend (UiRole) -> WidgetSnapshot?, show: Boolean, onDismissFinished: (() -> Unit)?,
    initialTheme: String, overviewPreview: Pair<String, WidgetSnapshot?>?, game: String,
    initialRoleKeys: Map<String, String>, initialSnapshots: Map<String, Pair<String, WidgetSnapshot?>>) {
    val pagerState = rememberPagerState(initialPage = widgetCatalogIndex(game, initialTheme)) { widgetCatalog.size }
    val scope = rememberCoroutineScope()
    var selectedKeys by rememberSaveable(show, stateSaver = RoleKeysSaver) {
        mutableStateOf(defaultRoleKeys(roles, initialRoleKeys, overviewPreview))
    }
    LaunchedEffect(show, game, initialTheme) { if (show) pagerState.scrollToPage(widgetCatalogIndex(game, initialTheme)) }
    val loaded = Branding.games.associate { (gameId, _) ->
        val gameRole = resolveRole(roles, selectedKeys, gameId)
        val fetched by key(gameId) {
            produceState<Pair<String, WidgetSnapshot?>?>(null, show, gameRole?.key) {
                value = null
                if (show && gameRole != null && gameRole.provider != FakeArknightsAdapter.ID) {
                    value = gameRole.key to previewSnapshot(gameRole)
                }
            }
        }
        gameId to fetched
    }
    val activeIndex by remember(pagerState) { derivedStateOf { pagerState.currentPage.coerceIn(0, widgetCatalog.lastIndex) } }
    val headerAlpha by remember(pagerState) {
        derivedStateOf { crossGameAlpha(pagerState.currentPage, pagerState.currentPageOffsetFraction) }
    }
    val settingsAlpha by remember(pagerState) {
        derivedStateOf { pageSettingsAlpha(pagerState.currentPage, pagerState.currentPageOffsetFraction, widgetCatalog.size) }
    }
    val (activeGame, activePalette) = widgetCatalog[activeIndex]
    val activeRole = resolveRole(roles, selectedKeys, activeGame)
    val palettes = StatusWidgetProvider.themes(widgetFeature(activeGame)).map { it.name }
    val targets = widgetCatalog.map { (pageGame, pageTheme) ->
        val pageRole = resolveRole(roles, selectedKeys, pageGame)
        val pageSnapshot = loaded[pageGame]?.takeIf { it.first == pageRole?.key }?.second
            ?: initialSnapshots[pageGame]?.takeIf { it.first == pageRole?.key }?.second
            ?: overviewPreview?.takeIf { it.first == pageRole?.key }?.second
        PreviewTarget(pageSnapshot, pageTheme == "DARK", "editor:$pageGame:${pageRole?.key ?: "empty"}:$pageTheme", pageGame)
    }
    val header = WidgetSheetHeader.New(activeGame, widgetName(activeGame), widgetDescription(activeGame))
    WidgetSheet(show, header, previews, null, busy, canConfirm = activeRole != null, error = error,
        onDismiss = onDismiss, onConfirm = { activeRole?.let { onSave(it, activePalette, false) } },
        onDismissFinished = onDismissFinished, headerAlpha = headerAlpha, settingsAlpha = settingsAlpha,
        previewContent = {
            WidgetPreviewPager(pagerState, previews, targets, !busy) { "config-preview:${widgetCatalog[it].second}" }
        }) {
        if (activeRole == null) Box(Modifier.padding(horizontal = WidgetSheetInset)) {
            val prompt = if (roles.isEmpty()) "请先添加${Branding.PLATFORM}账号" else "账号中还没有${Branding.gameName(activeGame)}角色"
            EmptyCard(prompt, "添加账号") { if (!busy) onAccounts() }
        } else ConfigSettings(roles.filter { it.game == activeGame }, activeRole,
            { selectedKeys = selectedKeys + (activeGame to it.key) }, activePalette.takeIf { palettes.size > 1 },
            { chosen ->
                scope.launch {
                    pagerState.animateScrollToPage(widgetCatalogIndex(activeGame, chosen),
                        animationSpec = spring(WidgetPagerMotion.SnapDamping, WidgetPagerMotion.SnapStiffness))
                }
            }, busy, replaceHint = false, failure = null, onFailure = {})
    }
}

@Composable
private fun ConfigEditorSingle(activity: Activity, roles: List<UiRole>, current: UiWidget?, busy: Boolean, error: String,
    onDismiss: () -> Unit, onAccounts: () -> Unit, onSave: (UiRole, String, Boolean) -> Unit,
    previews: WidgetPreviews, previewSnapshot: suspend (UiRole) -> WidgetSnapshot?,
    show: Boolean, onDismissFinished: (() -> Unit)?, showTools: Boolean,
    widgetId: Int, initialTheme: String, overviewPreview: Pair<String, WidgetSnapshot?>?, game: String) {
    val hostProvider = remember(activity, current?.id, widgetId) {
        AppWidgetManager.getInstance(activity).getAppWidgetInfo(current?.id ?: widgetId)?.provider
    }
    var key by rememberSaveable(show, current?.id) {
        mutableStateOf(roles.firstOrNull { current?.bindsTo(it) == true }?.key ?: roles.firstOrNull()?.key ?: "")
    }
    var theme by rememberSaveable(show, current?.id, widgetId, initialTheme) {
        mutableStateOf(current?.theme ?: if (hostProvider == null) initialTheme
            else if (StatusWidgetProvider.theme(activity, current?.id ?: widgetId) == WidgetTheme.DARK) "DARK" else "LIGHT")
    }
    // Only Arknights offers a palette; the Endfield card is always dark, like the official widget.
    val palettes = StatusWidgetProvider.themes(widgetFeature(game)).map { it.name }
    val palette = theme.takeIf { it in palettes } ?: palettes.first()
    var failure by rememberSaveable(show, current?.id) { mutableStateOf(current?.simulateFailure ?: false) }
    val role = roles.firstOrNull { it.key == key } ?: roles.firstOrNull()
    val preview by produceState<Pair<String, WidgetSnapshot?>?>(null, show, role?.key) {
        value = null
        // The debug fixture has no stored overview data to preview.
        if (show && role != null && role.provider != FakeArknightsAdapter.ID) value = role.key to previewSnapshot(role)
    }
    val snapshot = current?.snapshot?.takeIf { role != null && current.bindsTo(role) }
        ?: preview?.takeIf { it.first == role?.key }?.second
        ?: overviewPreview?.takeIf { current == null && it.first == role?.key }?.second
    val header = if (isNewWidget(current, widgetId))
        WidgetSheetHeader.New(game, widgetName(game), widgetDescription(game)) else WidgetSheetHeader.Edit
    // A placed instance keeps its provider's material, so a new palette only shows once the widget is placed again.
    val chosenProvider = StatusWidgetProvider.providerClass(widgetFeature(game), WidgetTheme.valueOf(palette))
    val replaceHint = hostProvider != null && hostProvider.className != chosenProvider.name
    val tools = showTools && role?.provider == FakeArknightsAdapter.ID && BuildConfig.DEBUG
    WidgetSheet(show, header, previews, role?.let { PreviewTarget(snapshot, palette == "DARK", "editor:${it.key}", game) },
        busy, canConfirm = role != null, error = error, onDismiss = onDismiss,
        onConfirm = { role?.let { onSave(it, palette, it.provider == FakeArknightsAdapter.ID && failure) } },
        onDismissFinished = onDismissFinished, previewModifier = Modifier.testTag("config-preview:$palette")) {
        if (role == null) Box(Modifier.padding(horizontal = WidgetSheetInset)) {
            EmptyCard("请先添加${Branding.PLATFORM}账号", "添加账号") { if (!busy) onAccounts() }
        } else ConfigSettings(roles, role, { key = it.key }, palette.takeIf { palettes.size > 1 }, { theme = it }, busy,
            replaceHint, failure.takeIf { tools }) { failure = it }
    }
}

// The settings rows, straight on the sheet. [palette] is null for a game with one palette; [failure] is the debug switch's
// value, or null when the switch is hidden.
@Composable
private fun ConfigSettings(roles: List<UiRole>, role: UiRole, onRole: (UiRole) -> Unit, palette: String?,
    onPalette: (String) -> Unit, busy: Boolean, replaceHint: Boolean, failure: Boolean?, onFailure: (Boolean) -> Unit) {
    TsukiDropdownPreference(title = "角色", items = roles.map { it.name }, selectedIndex = roles.indexOf(role),
        enabled = !busy, onSelectedIndexChange = { onRole(roles[it]) })
    if (palette != null) TsukiDropdownPreference(title = "配色", items = widgetThemes.map { it.second },
        selectedIndex = widgetThemes.indexOfFirst { it.first == palette }.coerceAtLeast(0),
        enabled = !busy, onSelectedIndexChange = { onPalette(widgetThemes[it].first) })
    // Lined up with the rows' text and the main button.
    if (replaceHint) MutedText("浅色与深色小部件使用不同的材质。更换配色后，请在桌面移除此小部件并重新添加。",
        modifier = Modifier.padding(start = WidgetSheetInset, end = WidgetSheetInset, top = HintGap))
    if (failure != null) Row(Modifier.padding(horizontal = WidgetSheetInset, vertical = HintGap),
        verticalAlignment = Alignment.CenterVertically) {
        Text("模拟刷新失败", Modifier.weight(1f), style = MiuixTheme.textStyles.headline2)
        Switch(failure, onFailure, enabled = !busy)
    }
}

private val HintGap = 8.dp

private val RoleKeysSaver = Saver<Map<String, String>, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString(it) },
)

private fun defaultRoleKeys(roles: List<UiRole>, initialRoleKeys: Map<String, String>,
    overviewPreview: Pair<String, WidgetSnapshot?>?): Map<String, String> =
    Branding.games.mapNotNull { (gameId, _) ->
        val candidate = initialRoleKeys[gameId]?.takeIf { key -> roles.any { it.key == key && it.game == gameId } }
            ?: overviewPreview?.first?.takeIf { key -> roles.any { it.key == key && it.game == gameId } }
            ?: roles.firstOrNull { it.game == gameId }?.key
        candidate?.let { gameId to it }
    }.toMap()

private fun resolveRole(roles: List<UiRole>, selectedKeys: Map<String, String>, gameId: String): UiRole? {
    val gameRoles = roles.filter { it.game == gameId }
    val key = selectedKeys[gameId]
    return gameRoles.firstOrNull { it.key == key } ?: gameRoles.firstOrNull()
}
