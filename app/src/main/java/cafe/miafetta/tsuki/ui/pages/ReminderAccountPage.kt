package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.accountTitle
import cafe.miafetta.tsuki.ui.glass.rememberNavActive
import cafe.miafetta.tsuki.ui.state.ReminderAccounts
import cafe.miafetta.tsuki.ui.state.ReminderSettings
import cafe.miafetta.tsuki.ui.state.UiAccount

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card

// "通知提醒" → an account: titled with the account's name as "我的" shows it, then a group per role of the account
// (ReminderAccounts.options) with the rows of its game's metrics (ReminderMetricRows). The roles' limits come from their
// cached snapshots, read as the page opens. [account] is null while the accounts load and once the account is gone
// (deleted, or no longer listed); once [loaded] the page then leaves through [onGone], still showing the account it had.
@Composable
internal fun ReminderAccountPage(settings: ReminderSettings, account: UiAccount?, loaded: Boolean, blurEnabled: Boolean,
    onGone: () -> Unit, onBack: () -> Unit) {
    val active = rememberNavActive()
    // The page below is not resumed while this one is on top, so this one reads the state on every return as well; after
    // a recreation on this page it is the first read of the rules.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settings.resume() }
    var last by remember { mutableStateOf(account) }
    SideEffect { if (account != null) last = account }
    val shown = account ?: last
    val gone = loaded && account == null
    LaunchedEffect(gone) { if (gone) onGone() }
    val options = remember(shown) { shown?.let(ReminderAccounts::options).orEmpty() }
    LaunchedEffect(options) { settings.loadLimits(options) }
    SecondaryScaffold(shown?.let(::accountTitle).orEmpty(), blurEnabled, active, onBack,
        modifier = Modifier.testTag("reminder-account-page"), contentPadding = PaddingValues(horizontal = 12.dp)) {
        if (shown != null && options.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth().testTag("reminder-no-roles")) {
                BasicComponent(summary = ReminderAccounts.NO_ROLES_PAGE)
            }
        }
        ReminderMetricRows(settings, options, active)
    }
}
