package cafe.miafetta.tsuki.ui.sheets

import cafe.miafetta.tsuki.ui.components.EmptyCard
import cafe.miafetta.tsuki.ui.components.MutedText
import cafe.miafetta.tsuki.ui.components.WidgetPreviewStage
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.ui.theme.widgetThemes
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.ui.state.UiWidget

import cafe.miafetta.tsuki.ui.components.TsukiDropdownPreference
import cafe.miafetta.tsuki.ui.state.UiRole

import android.app.Activity
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.*
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
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ConfigEditor(activity: Activity, roles: List<UiRole>, current: UiWidget?, busy: Boolean, error: String,
    onDismiss: () -> Unit, onAccounts: () -> Unit, onSave: (UiRole, String, Boolean) -> Unit,
    previews: WidgetPreviews, previewSnapshot: suspend (UiRole) -> WidgetSnapshot?,
    show: Boolean = true, onDismissFinished: (() -> Unit)? = null, showTools: Boolean = false,
    widgetId: Int = -1, initialTheme: String = "LIGHT", overviewPreview: Pair<String, WidgetSnapshot?>? = null) {
    val hostProvider = remember(activity, current?.id, widgetId) {
        AppWidgetManager.getInstance(activity).getAppWidgetInfo(current?.id ?: widgetId)?.provider
    }
    var key by rememberSaveable(show, current?.id) { mutableStateOf(roles.firstOrNull { it.accountId == current?.accountId &&
        it.id == current.roleId }?.key ?: roles.firstOrNull()?.key ?: "") }
    var theme by rememberSaveable(show, current?.id, widgetId, initialTheme) {
        mutableStateOf(current?.theme ?: if (hostProvider == null) initialTheme
            else if (hostProvider.className == OpaqueWidgetProvider::class.java.name) "DARK" else "LIGHT")
    }
    var failure by rememberSaveable(show, current?.id) { mutableStateOf(current?.simulateFailure ?: false) }
    val role = roles.firstOrNull { it.key == key } ?: roles.firstOrNull()
    val preview by produceState<Pair<String, WidgetSnapshot?>?>(null, show, role?.key) {
        value = null
        if (show && role?.provider == SklandArknightsAdapter.ID) value = role.key to previewSnapshot(role)
    }
    val snapshot = current?.snapshot?.takeIf { role != null && current.accountId == role.accountId && current.roleId == role.id }
        ?: preview?.takeIf { it.first == role?.key }?.second
        ?: overviewPreview?.takeIf { current == null && it.first == role?.key }?.second
    OverlayBottomSheet(show = show, title = if (current == null) "添加小部件" else "编辑小部件",
        backgroundColor = MiuixTheme.colorScheme.surface, sheetMaxWidth = 600.dp,
        startAction = { IconButton(onClick = { if (!busy) onDismiss() }, enabled = !busy) { Icon(MiuixIcons.Close, "关闭") } },
        endAction = { IconButton(onClick = {
                if (!busy) role?.let { onSave(it, theme, it.provider == FakeArknightsAdapter.ID && failure) }
            },
            enabled = role != null && !busy, modifier = Modifier.testTag("config-action")) { Icon(MiuixIcons.Ok, "保存") } },
        onDismissRequest = { if (!busy) onDismiss() }, onDismissFinished = onDismissFinished, allowDismiss = !busy) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (role == null) EmptyCard("请先添加${Branding.PLATFORM}账号", "添加账号") { if (!busy) onAccounts() }
            else {
                WidgetPreviewStage(previews, snapshot, theme == "DARK", "editor:${role.key}",
                    imageModifier = Modifier.testTag("config-preview:$theme"))
                Card {
                    TsukiDropdownPreference(title = "角色", items = roles.map { it.name }, selectedIndex = roles.indexOf(role),
                        enabled = !busy, onSelectedIndexChange = { key = roles[it].key })
                    TsukiDropdownPreference(title = "配色", items = widgetThemes.map { it.second },
                        selectedIndex = widgetThemes.indexOfFirst { it.first == theme }.coerceAtLeast(0),
                        enabled = !busy, onSelectedIndexChange = { theme = widgetThemes[it].first })
                }
                if (hostProvider != null && hostProvider.className != StatusWidgetProvider.providerClass(WidgetTheme.valueOf(theme)).name)
                    // The sheet already has a 24 dp inside margin
                    // align with the card content (24 + 16 dp).
                    MutedText("浅色与深色小部件使用不同的材质。更换配色后，请在桌面移除此小部件并重新添加。", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
                if (showTools && role.provider == FakeArknightsAdapter.ID && BuildConfig.DEBUG)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("模拟刷新失败", Modifier.weight(1f), style = MiuixTheme.textStyles.headline2)
                        Switch(failure, { failure = it }, enabled = !busy)
                    }
            }
            if (error.isNotEmpty()) Text(error, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error)
        }

        }
    }
}

