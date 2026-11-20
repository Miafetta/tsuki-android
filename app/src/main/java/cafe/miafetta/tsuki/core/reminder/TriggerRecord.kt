package cafe.miafetta.tsuki.core.reminder

// What one rule has already reported. There is one record per rule (a role and a metric), however many widgets show
// the role, and it is persisted with the rule, so it holds plain values only.
//
// [mode] and [threshold] are those of the rule the record was kept for: a record kept for another mode or threshold
// (including the empty record) starts over as if the rule had just been enabled. [fired] means the condition was
// reported and has not been seen unmet in fresh data since; only fresh data re-arms it. [firedAt] is the local time of
// the last report and [firedRound] the event round it reported. [dataAt] is the data time of the latest fresh
// observation that was judged; older data arriving later is ignored.
data class TriggerRecord(
    val mode: ReminderMode? = null,
    val threshold: Int? = null,
    val fired: Boolean = false,
    val firedAt: Long? = null,
    val firedRound: String? = null,
    val dataAt: Long? = null,
) {
    fun keptFor(rule: ReminderRule) = mode == rule.mode && threshold == rule.threshold
}
