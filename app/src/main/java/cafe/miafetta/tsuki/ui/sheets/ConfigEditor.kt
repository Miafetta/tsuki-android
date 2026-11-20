package cafe.miafetta.tsuki.ui.sheets

import cafe.miafetta.tsuki.ui.components.EmptyCard
import cafe.miafetta.tsuki.ui.components.MutedText
import cafe.miafetta.tsuki.ui.components.PreviewTarget
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.components.WidgetSheet
import cafe.miafetta.tsuki.ui.components.WidgetSheetHeader
import cafe.miafetta.tsuki.ui.components.WidgetSheetInset
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.ui.theme.widgetDescription
import cafe.miafetta.tsuki.ui.theme.widgetName
import cafe.miafetta.tsuki.ui.theme.widgetThemes
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.ui.state.UiWidget

import cafe.miafetta.tsuki.ui.components.TsukiDropdownPreference
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.state.widgetFeature

import android.app.Activity
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.widget.*
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
    game: String = Branding.GAME_ID) {
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
