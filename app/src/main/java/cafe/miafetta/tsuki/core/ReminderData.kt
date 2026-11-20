package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord

// Everything ReminderStore keeps: the master switch, one entry per role and metric (a rule and its trigger record), and
// the check times of roles. Reminders belong to roles, never to widgets.
//
// Check times outlive the rules of their role, so that removing and adding a rule again cannot shorten the 30-minute
// request interval (docs/DEVELOPMENT_CONSTRAINTS.md §2); they go with the role when its account or the role goes.
internal data class ReminderData(
    val enabled: Boolean = false,
    val entries: List<ReminderEntry> = emptyList(),
    val checks: Map<ReminderSubject, ReminderCheckTimes> = emptyMap(),
) {
    init {
        require(entries.distinctBy { it.rule.subject to it.rule.metricId }.size == entries.size)
    }

    val subjects: Set<ReminderSubject> get() = entries.map { it.rule.subject }.toSet() + checks.keys

    fun entry(subject: ReminderSubject, metricId: String) =
        entries.firstOrNull { it.rule.subject == subject && it.rule.metricId == metricId }
}

internal data class ReminderEntry(val rule: ReminderRule, val record: TriggerRecord = TriggerRecord())

// When a reminder job last calibrated and last requested a role, in local epoch milliseconds (null: never). These are
// the inputs of ReminderSchedule.nextCheck; the reminder jobs write them.
internal data class ReminderCheckTimes(val lastCalibrationAt: Long? = null, val lastRequestAt: Long? = null) {
    init {
        require((lastCalibrationAt ?: 0) >= 0 && (lastRequestAt ?: 0) >= 0)
    }
}
