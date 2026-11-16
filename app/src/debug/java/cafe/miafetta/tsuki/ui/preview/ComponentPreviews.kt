package cafe.miafetta.tsuki.ui.preview

import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.state.UiAccount
import cafe.miafetta.tsuki.ui.components.EmptyGuide
import cafe.miafetta.tsuki.ui.components.GameChips
import cafe.miafetta.tsuki.ui.components.MetricCard
import cafe.miafetta.tsuki.ui.components.MetricRow
import cafe.miafetta.tsuki.ui.components.ProgressRing
import cafe.miafetta.tsuki.ui.components.RoleCard
import cafe.miafetta.tsuki.ui.components.SyncTag
import cafe.miafetta.tsuki.ui.components.TrainingCard
import cafe.miafetta.tsuki.ui.components.TsukiDropdownPreference
import cafe.miafetta.tsuki.ui.components.TsukiMenuContent
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.components.TsukiTabRow
import cafe.miafetta.tsuki.ui.components.TsukiTopBar
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.components.WidgetStage
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.ui.theme.widgetThemes
import cafe.miafetta.tsuki.ui.glass.BottomWash
import cafe.miafetta.tsuki.ui.glass.GameHeaderFade
import cafe.miafetta.tsuki.ui.glass.TsukiGlass
import cafe.miafetta.tsuki.ui.pages.AccountCard

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.WidgetMetric
import top.yukonga.miuix.kmp.basic.*

// Every component preview renders once per app theme with fixed sample data; nothing reads AppState, storage or network.
private class ThemeProvider : PreviewParameterProvider<String> {
    override val values = sequenceOf("LIGHT", "DARK")
}

private val role = UiRole("sample", "森空岛主账号", "100000001", "测试博士 · 官服")

// Expanded and at rest; the drawn titles carry no blur there.
@Preview(name = "TopBar", widthDp = 360, showBackground = true)
@Composable
private fun TopBarPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { TsukiTopBar("概览", MiuixScrollBehavior()) }
}

// Padded so the frame does not cut the dock's outer shadow (36.5dp out).
@Preview(name = "NavDock", widthDp = 360, showBackground = true)
@Composable
private fun NavDockPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { Box(Modifier.padding(vertical = 40.dp)) { TsukiNavDock(0, {}) } }
}

@Preview(name = "TabRow", widthDp = 360, showBackground = true)
@Composable
private fun TabRowPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { Box(Modifier.padding(12.dp)) { TsukiTabRow(listOf("已添加 2", "全部小部件"), 0, {}, "catalog") } }
}

// Padded so the frame does not cut the selected chip's shadow (15dp out, past the row's own 4dp and 10dp).
@Preview(name = "GameChips", widthDp = 360, showBackground = true)
@Composable
private fun GameChipsPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) {
        Box(Modifier.padding(vertical = 12.dp)) { GameChips(listOf(Branding.GAME_ID to Branding.GAME), Branding.GAME_ID, {}) }
    }
}

@Preview(name = "ProgressRing", widthDp = 360, showBackground = true)
@Composable
private fun ProgressRingPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { ProgressRing(.7f) }
}

@Preview(name = "SyncTag", widthDp = 360, showBackground = true)
@Composable
private fun SyncTagPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { SyncTag("3 分钟前") }
}

@Preview(name = "RoleCard", widthDp = 360, showBackground = true)
@Composable
private fun RoleCardPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) {
        RoleCard(listOf(role, role.copy(id = "100000002", label = "小号博士 · B服")), role, "3 分钟前", false, {})
    }
}

@Preview(name = "MetricCard", widthDp = 360, showBackground = true)
@Composable
private fun MetricCardPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { MetricCard("理智", WidgetMetric("理智", 27, 210), "18小时18分钟后全部恢复", "明天 13:26") }
}

@Preview(name = "TrainingCard", widthDp = 360, showBackground = true)
@Composable
private fun TrainingCardPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) {
        TrainingCard("空闲中", "暂无专精训练") { Image(painterResource(R.drawable.widget_training_light), null, it) }
    }
}

@Preview(name = "MetricRow", widthDp = 360, showBackground = true)
@Composable
private fun MetricRowPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { Card { MetricRow("每日任务", WidgetMetric("每日任务", 7, 10), "8小时52分钟后刷新") } }
}

// Production pages fill the stage with WidgetPreviewStage; the preview uses a fixed content slot instead of IPC.
@Preview(name = "WidgetStage", widthDp = 360, showBackground = true)
@Composable
private fun WidgetStagePreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) {
        WidgetStage { Box(Modifier.fillMaxWidth().aspectRatio(1028f / 462f)) { Text("未同步角色，所有指标未知") } }
    }
}

@Preview(name = "EmptyGuide", widthDp = 360, showBackground = true)
@Composable
private fun EmptyGuidePreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { EmptyGuide({}) }
}

// Only the menu content: previews cannot host the popup window.
@Preview(name = "TsukiMenu", widthDp = 360, showBackground = true)
@Composable
private fun TsukiMenuPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    val items = listOf(DropdownItem("测试博士", selected = true, summary = "森空岛主账号，官服"),
        DropdownItem("小号博士", summary = "森空岛主账号，B服"))
    TsukiAppTheme(theme) { Scaffold { Box(Modifier.padding(it).size(240.dp, 240.dp)) { TsukiMenuContent(items, {}) } } }
}

@Preview(name = "DropdownPreference", widthDp = 360, showBackground = true)
@Composable
private fun DropdownPreferencePreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { Card { TsukiDropdownPreference("配色", widgetThemes.map { it.second }, 0, {}) } }
}

@Preview(name = "TsukiGlass", widthDp = 360, showBackground = true)
@Composable
private fun TsukiGlassPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    // No glass host in a preview, so this is the opaque fallback of the home page header.
    TsukiAppTheme(theme) {
        TsukiGlass({ 1f }, 24.dp, Modifier.fillMaxWidth().height(24.dp + TopAppBarDefaults.CollapsedHeight + GameHeaderFade),
            gameHeader = true)
    }
}

@Preview(name = "BottomWash", widthDp = 360, showBackground = true)
@Composable
private fun BottomWashPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { BottomWash(Modifier.fillMaxWidth().height(92.dp)) }
}

@Preview(name = "AccountCard", widthDp = 360, showBackground = true)
@Composable
private fun AccountCardPreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { AccountCard(UiAccount("sample", "森空岛主账号", listOf(role)), emptyList(), false, {}, {}) }
}

@Preview(name = "SmallTitle", widthDp = 360, showBackground = true)
@Composable
private fun SmallTitlePreview(@PreviewParameter(ThemeProvider::class) theme: String) {
    TsukiAppTheme(theme) { Box(Modifier.padding(horizontal = 12.dp)) { TsukiSmallTitle("实时数据") } }
}
