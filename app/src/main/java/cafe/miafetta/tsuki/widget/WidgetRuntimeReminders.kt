package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.ReminderData
import cafe.miafetta.tsuki.core.ReminderStore
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandRole

// Reminder rules as the settings page reads and edits them, and their cleanup when accounts change. Every read and
// write holds the runtime lock, as account changes and reminder checking (WidgetRuntimeReminderChecks.kt) do, so a
// rule is never saved for a role that an account change is removing at the same time. Every change reschedules the
// reminder job once the lock is released.

internal fun WidgetRuntime.reminderData(): ReminderData = synchronized(this) { reminders.read() }

internal fun WidgetRuntime.saveReminder(rule: ReminderRule): ReminderData = synchronized(this) {
    require(ReminderAccess.accepts(rule, accounts.list())) { "Reminder rule not accepted" }
    reminders.save(rule)
}.also { rescheduleReminders() }

internal fun WidgetRuntime.deleteReminder(subject: ReminderSubject, metricId: String): ReminderData =
    synchronized(this) { reminders.delete(subject, metricId) }.also { rescheduleReminders() }

// The channels exist from the moment reminders are on, so that the settings page can show and open them. Switching on
// again while on changes no rule but still reschedules, for example after the notification permission was granted.
internal fun WidgetRuntime.setRemindersEnabled(enabled: Boolean): ReminderData {
    if (enabled) reminderServices.notifier.createChannels()
    return synchronized(this) { reminders.setEnabled(enabled) }.also { rescheduleReminders() }
}

// The cleanup below runs inside the lock of the account change (WidgetRuntimeAccounts.kt), after that change has been
// saved, and reschedules there: the roles removed are no longer planned. A reminder file that cannot be read must not
// fail the change: it is left as it is, for the settings page to report, rather than replaced with an empty one, and
// since its roles are unknown the reminder job is cancelled (rescheduling cannot read it either).

// Signing out: every rule and check time of the account goes.
internal fun WidgetRuntime.forgetReminders(accountId: String) = cleanReminders { deleteAccount(accountId) }

// Signing in again or updating the Cred: the account's roles that are gone take their rules and check times with them;
// the roles still on the account keep theirs, records included.
internal fun WidgetRuntime.retainReminders(accountId: String, roles: List<SklandRole>) =
    cleanReminders { retainRoles(accountId, ReminderAccess.subjects(accountId, roles)) }

// Resetting the accounts: every rule and check time goes; the master switch keeps its value, or is off when the file
// could not be read (ReminderStore.clear).
internal fun WidgetRuntime.clearReminders() {
    reminders.clear()
    rescheduleReminders()
}

private inline fun WidgetRuntime.cleanReminders(remove: ReminderStore.() -> Set<ReminderSubject>) {
    val removed = try {
        reminders.remove()
    } catch (_: DataSourceFailure) {
        rescheduleReminders()
        return
    }
    if (removed.isNotEmpty()) rescheduleReminders()
}
