package cafe.miafetta.tsuki.core.reminder

// The result of judging one rule: the notification to send (null for none), the record to keep in place of the old
// one, and when this rule wants the role checked next (null: no time of its own, the calibration period applies).
data class ReminderDecision(
    val status: ReminderStatus,
    val record: TriggerRecord,
    val notice: ReminderNotice? = null,
    val expectedAt: Long? = null,
) {
    val notify get() = notice != null
}
