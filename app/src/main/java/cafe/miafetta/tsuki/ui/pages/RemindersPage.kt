package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.AccountRow
import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.state.ReminderAccounts
import cafe.miafetta.tsuki.ui.state.ReminderProblem
import cafe.miafetta.tsuki.ui.state.ReminderSettings
import cafe.miafetta.tsuki.ui.state.ReminderSystem
import cafe.miafetta.tsuki.ui.state.UiAccount
import cafe.miafetta.tsuki.ui.state.UiReminders
import cafe.miafetta.tsuki.ui.theme.Branding

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Necessary permission copy (UI_DESIGN_CONVENTIONS keeps it): reminders run as deferred background checks, and how
// late they may run depends on system settings that only the user can change.
private const val BackgroundNote = "提醒在后台定时检查，系统可能延迟执行。如果提醒不及时，可以在系统设置中允许 Tsuki 自启动，" +
    "并把省电策略设为无限制。"

// "我的" → "通知提醒": the master switch, what keeps notifications from showing, the accounts, and last how background
// checks run. Each account opens its own page ([onAccount], ReminderAccountPage) with the reminders of its roles.
// Rules can be edited while reminders are off; they apply once the switch is on. The page has no text field, so it
// takes no IME padding.
@Composable
internal fun RemindersPage(settings: ReminderSettings, accounts: List<UiAccount>, blurEnabled: Boolean,
    onAccount: (UiAccount) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val transition = LocalNavTransitionScope.current
    val active by remember(transition) { derivedStateOf { !transition.isRunning && transition.relativeDepth == 0f } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        settings.permissionAnswered()
    }
    // Also runs when the page first composes, since the activity is already resumed, and when an account page closes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settings.resume() }
    fun open(intents: List<Intent>) {
        if (active && !ReminderSystem.open(context, intents)) settings.reportUnavailable()
    }
    SecondaryScaffold("通知提醒", blurEnabled, active, onBack, modifier = Modifier.testTag("reminders-page"),
        contentPadding = PaddingValues(horizontal = 12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            // Like the metric rows, the switch keeps its look while its save runs (a disabled flash read as the text
            // vanishing); a tap meanwhile is ignored.
            SwitchPreference(settings.reminders.enabled, { on ->
                if (active && !settings.isBusy(ReminderSettings.MASTER)) {
                    settings.setEnabled(on)
                    if (on && settings.needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }, "通知提醒")
        }
        ReminderStatus(settings.problems) { open(listOf(ReminderSystem.settings(context, it))) }
        ReminderAccountList(settings.reminders, accounts) { if (active) onAccount(it) }
        TsukiSmallTitle("后台运行")
        Card(Modifier.fillMaxWidth().testTag("reminder-background")) {
            ArrowPreference("应用信息", onClick = { open(ReminderSystem.appDetails(context)) })
        }
        // A note under the card, outside it, like the closing notes of InfoPage.
        Text(BackgroundNote, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun ReminderStatus(problems: List<ReminderProblem>, onOpen: (ReminderProblem) -> Unit) {
    if (problems.isEmpty()) return
    TsukiSmallTitle("状态")
    Card(Modifier.fillMaxWidth().testTag("reminder-status")) {
        problems.forEach { problem -> ArrowPreference(problem.title, onClick = { onOpen(problem) }) }
    }
}

// Every account as "我的" lists them, in one card under the platform's account title, each row with how many of its
// reminders are on (ReminderAccounts.summary). Without an account a single untitled card says how to get one.
@Composable
private fun ReminderAccountList(reminders: UiReminders, accounts: List<UiAccount>, onOpen: (UiAccount) -> Unit) {
    if (accounts.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().testTag("reminder-empty")) {
            BasicComponent(summary = "添加账号后可以设置提醒")
        }
        return
    }
    TsukiSmallTitle("${Branding.PLATFORM}账号")
    Card(Modifier.fillMaxWidth().testTag("reminder-accounts")) {
        accounts.forEach { account ->
            key(account.id) {
                AccountRow(account, ReminderAccounts.summary(account, reminders), Modifier.testTag("reminder-account:${account.id}"),
                    onClick = { onOpen(account) })
            }
        }
    }
}
