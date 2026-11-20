package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.theme.AppLogo
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.ui.components.AccountRow
import cafe.miafetta.tsuki.ui.components.EmptyCard
import cafe.miafetta.tsuki.ui.components.PageListSpacing
import cafe.miafetta.tsuki.ui.state.UiAccount
import cafe.miafetta.tsuki.ui.state.UiWidget
import cafe.miafetta.tsuki.ui.components.TsukiDropdownPreference
import cafe.miafetta.tsuki.ui.components.TsukiIconMenu
import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.BuildConfig
import cafe.miafetta.tsuki.core.RefreshFailure
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.mePage(accounts: List<UiAccount>, widgets: List<UiWidget>, busy: Boolean,
    theme: String, blur: Boolean, onTheme: (String) -> Unit, onBlur: (Boolean) -> Unit,
    onAdd: () -> Unit, onUpdate: (UiAccount) -> Unit, onDelete: (UiAccount) -> Unit,
    onPrivacy: () -> Unit, onLicenses: () -> Unit, onReminders: () -> Unit = {},
    onSimulation: () -> Unit = {}, onProbe: () -> Unit = {}, onReset: () -> Unit = {}, showTools: Boolean = false,
    authRoleKeys: Set<String> = emptySet()) {
    // The page opens with this title, and its cards are the next items (AppRoot counts these items to scroll to 外观).
    item("accounts-heading") { TsukiSmallTitle("${Branding.PLATFORM}账号", first = true, spacingBelow = PageListSpacing) }
    if (accounts.isEmpty()) item("no-accounts") { EmptyCard("还没有${Branding.PLATFORM}账号", "添加账号", onAdd) }
    accounts.forEach { account -> item("account:${account.id}") {
        AccountCard(account, widgets, busy, { onUpdate(account) }, { onDelete(account) }, authRoleKeys)
    } }
    item("appearance") { Column {
        TsukiSmallTitle("外观", Modifier.testTag("section-title:appearance"), spacingAbove = PageListSpacing)
        Card(Modifier.fillMaxWidth().testTag("settings-appearance")) {
            val modes = listOf("SYSTEM", "LIGHT", "DARK")
            TsukiDropdownPreference("深色模式", listOf("跟随系统", "关闭", "开启"), modes.indexOf(theme).coerceAtLeast(0), { onTheme(modes[it]) })
            SwitchPreference(blur, onBlur, "界面模糊效果")
        }
    } }
    item("notifications") { Column {
        TsukiSmallTitle("通知", spacingAbove = PageListSpacing)
        Card(Modifier.fillMaxWidth()) { ArrowPreference("通知提醒", onClick = onReminders) }
    } }
    item("about") { Column {
        TsukiSmallTitle("关于", spacingAbove = PageListSpacing)
        Card {
            ArrowPreference("数据与隐私", onClick = onPrivacy)
            ArrowPreference("开源许可", onClick = onLicenses)
            BasicComponent(title = "版本", endActions = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantActions) })
        }
    } }
    if (showTools && BuildConfig.DEBUG) item("diagnostics") { Column {
        TsukiSmallTitle("开发诊断", spacingAbove = PageListSpacing)
        Card {
            ArrowPreference("添加模拟角色小部件", onClick = onSimulation, enabled = !busy)
            ArrowPreference("添加 HyperOS 测试小部件", onClick = onProbe, enabled = !busy)
            ArrowPreference("重置账号存储", onClick = onReset, enabled = !busy)
        }
    } }
}

@Composable
internal fun AccountCard(account: UiAccount, widgets: List<UiWidget>, busy: Boolean, onUpdate: () -> Unit,
    onDelete: () -> Unit, authRoleKeys: Set<String> = emptySet()) {
    Card(Modifier.fillMaxWidth().testTag("account-card:${account.id}")) {
        AccountRow(account, "${account.roles.size} 个角色", endActions = {
            TsukiIconMenu(MiuixIcons.More, "账号菜单",
                listOf(DropdownItem("更新 Cred", onClick = onUpdate), DropdownItem("删除账号", onClick = onDelete)),
                enabled = !busy, destructiveIndex = 1, dividerBefore = 1)
        })
        account.roles.forEach { role ->
            val bound = widgets.filter { it.bindsTo(role) }
            val auth = role.key in authRoleKeys || bound.any { it.failure == RefreshFailure.AUTH_EXPIRED.name }
            BasicComponent(startAction = {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { AppLogo(size = 34, game = role.game) }
                },
                endActions = { Text(if (auth) "需重新授权" else "${bound.size} 个小部件", style = MiuixTheme.textStyles.body2,
                    color = if (auth) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary) }) {
                Text(role.name, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium)
                Text("${role.server} UID ${role.displayId}", style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
