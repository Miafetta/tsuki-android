package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.sheets.ConfigEditor
import cafe.miafetta.tsuki.ui.sheets.ConfirmationDialog
import cafe.miafetta.tsuki.ui.state.Confirmation
import cafe.miafetta.tsuki.ui.state.TrainingPortraitSource
import cafe.miafetta.tsuki.ui.state.UiAccount
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.state.UiWidget
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.pages.homePage
import cafe.miafetta.tsuki.ui.pages.mePage
import cafe.miafetta.tsuki.ui.pages.widgetPage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.core.TrainingState
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTraining

// The page fixture: production pages and sheets fed by AppUiVerification's in-memory state.
internal fun fixtureSnapshot(): WidgetSnapshot {
    val now = System.currentTimeMillis()
    return WidgetSnapshot("测试博士", listOf(
        WidgetMetric("理智", 27, 210, now + 65_700_000, estimated = true), WidgetMetric("公开招募", 0, 4),
        WidgetMetric("合成玉", 1800, 1800), WidgetMetric("每日任务", 7, 10),
        WidgetMetric("无人机", 90, 250), WidgetMetric("休息", 17, 19), WidgetMetric("订单", 2, 20),
        WidgetMetric("制造", 8, 64), WidgetMetric("疲劳", 2)), now,
        training = WidgetTraining(TrainingState.COMPLETE, "char_test_trainee", "测试干员"),
        dashboardMetrics = listOf(WidgetMetric("每周任务", 13, 13), WidgetMetric("数据增补仪", 0, 24),
            WidgetMetric("数据增补条", 0, 60), WidgetMetric("公招刷新", 2, 3), WidgetMetric("线索收集", 7, 7)))
}

@Composable
internal fun AppUiVerification.FixtureApp(host: MainActivity, redesignV4: String?) {
    val uiDensity = if (redesignV4 == "dock") host.resources.displayMetrics.density
        else host.resources.displayMetrics.widthPixels / 360f
    CompositionLocalProvider(LocalDensity provides Density(uiDensity, fontScale.value)) {
        TsukiAppTheme(theme.value) {
            val displayRole = if (longNames.value) role.copy(accountName = "这是用于检查窄屏文字换行的森空岛账号名称",
                label = "这是用于检查长角色名称的测试博士 · 官服") else role
            val second = displayRole.copy(id = "100000002", label = "第二角色")
            val roles = if (emptyState.value) emptyList() else listOf(displayRole, second)
            val homeSnapshot = trainingCase.value?.let { snapshot.copy(training = it) } ?: snapshot
            AccountNavigationFixture(host, dialog.value == "account",
                onDismiss = { dialog.value = "" }) { accountPageActive ->
                AppScaffold(page.intValue, blurEnabled = blur.value, canRefresh = canRefresh.value, showRefresh = true,
                    refreshing = refreshing.value, onRefresh = { fixtureRefresh(host, redesignV4) },
                    onNavigate = {
                        page.intValue = it
                        if (it == 1) previewVersion.intValue++
                    },
                    onAdd = { dialog.value = if (page.intValue == 2) "account" else "widget-new" },
                    addButtonVisible = !accountPageActive,
                    overlays = { FixtureOverlays(host, roles, homeSnapshot) }) { visiblePage ->
                    when (visiblePage) {
                        0 -> homePage(roles, roles.getOrNull(selectedRole.intValue) ?: roles.firstOrNull(), homeSnapshot,
                            { selectedRole.intValue = roles.indexOf(it) }, {}, previews = previews(),
                            portraits = TrainingPortraitSource(host)::load)
                        1 -> widgetPage(host, if (emptyState.value) emptyList()
                            else listOf(UiWidget(1, role.accountId, role.id, role.provider, "LIGHT", snapshot, "", false)),
                            snapshot, catalog.intValue, previews(), role.key,
                            { catalog.intValue = it }) { widget, chosenTheme ->
                                configTheme.value = chosenTheme
                                dialog.value = if (widget == null) "widget-new" else "widget"
                            }
                        2 -> mePage(if (emptyState.value) emptyList()
                            else listOf(UiAccount(displayRole.accountId, displayRole.accountName, roles)),
                            emptyList(), false,
                            theme.value, blur.value, { theme.value = it }, { blur.value = it },
                            { dialog.value = "account" }, { dialog.value = "account" }, { deleting.value = true }, {}, {})
                    }
                }
            }
        }
    }
}

// The top-refresh scenario requires the list to be back at its first item when the refresh starts.
private fun AppUiVerification.fixtureRefresh(host: MainActivity, redesignV4: String?) {
    if (redesignV4 == "top-refresh") {
        val list = composeNodes(host).single {
            it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:0"
        }
        check(list.config[SemanticsProperties.VerticalScrollAxisRange].value() == 0f) {
            "Refresh started before returning to the first item"
        }
        refreshing.value = true
        canRefresh.value = false
    }
    refreshClicks++
}

@Composable
private fun AppUiVerification.FixtureOverlays(host: MainActivity, roles: List<UiRole>, homeSnapshot: WidgetSnapshot) {
    ConfirmationDialog(Confirmation("删除账号", "删除测试账号及其角色和关联部件绑定。",
        cafe.miafetta.tsuki.StorageContract.LOGOUT, android.os.Bundle()),
        deleting.value, "", false, { deleting.value = false },
        { error("Deletion must never be confirmed by UI verification") })
    ConfigEditor(host, roles,
        if (dialog.value == "widget") UiWidget(1, role.accountId, role.id, role.provider, "LIGHT",
            snapshot, "", false) else null,
        configBusy.value, "", { if (!configBusy.value) dialog.value = "" }, {},
        { selected, chosenTheme, _ ->
            savedConfigs += selected.key to chosenTheme
            dialog.value = ""
        },
        previews = previews(), previewSnapshot = { null },
        show = dialog.value.startsWith("widget"), initialTheme = configTheme.value,
        overviewPreview = roles.getOrNull(selectedRole.intValue)?.key?.let { it to homeSnapshot })
}
