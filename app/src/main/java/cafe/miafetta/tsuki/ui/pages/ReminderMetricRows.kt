package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.state.ReminderOptions
import cafe.miafetta.tsuki.ui.state.ReminderRoleOption
import cafe.miafetta.tsuki.ui.state.ReminderSettings
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.state.reminderSubject

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cafe.miafetta.tsuki.core.reminder.ReminderCapability
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference

// The metric rows of an account's roles: one titled card per role, in ReminderAccounts order, with the rows of each
// metric of its game in ReminderOptions.rows order. A metric with a single mode is a switch; the others choose their
// condition from a menu, with the threshold under it (ReminderRuleRows).
@Composable
internal fun ReminderMetricRows(settings: ReminderSettings, options: List<ReminderRoleOption>, active: Boolean) {
    options.forEachIndexed { index, option ->
        key(option.key) {
            // The account page opens with its first role's title.
            TsukiSmallTitle(option.label, first = index == 0)
            Card(Modifier.fillMaxWidth().testTag("reminder-role:${option.key}")) {
                ReminderOptions.rows(option.game).forEach { capability ->
                    key(capability.metricId) {
                        val modifier = Modifier.testTag("reminder-row:${capability.metricId}")
                        if (ReminderOptions.isSwitch(capability)) MetricSwitch(settings, option.role, capability, active, modifier)
                        else ReminderRuleRows(settings, option.role, capability, active, modifier)
                    }
                }
            }
        }
    }
}

// The switch of a metric with a single mode (训练室专精训练完成, 公开招募全部完成): on saves that mode, off deletes the
// rule. Like the condition menus it keeps its look while its save runs; ReminderSettings ignores a tap meanwhile.
@Composable
private fun MetricSwitch(settings: ReminderSettings, role: UiRole, capability: ReminderCapability, active: Boolean,
    modifier: Modifier) {
    val subject = remember(role) { role.reminderSubject() }
    val on = settings.reminders.rule(subject, capability.metricId)?.enabled == true
    SwitchPreference(on, { if (active) settings.setOn(role, capability, it) }, ReminderOptions.title(capability), modifier)
}
