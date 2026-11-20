package cafe.miafetta.tsuki.core.reminder

// One reminder: a metric of a role and the one mode it is met by. PERCENT takes a whole percent and VALUE a value of
// at least one; FULL and EVENT take no threshold. Whether the metric offers the mode is ReminderCapabilities.check;
// a value threshold above the metric's current limit is caught when the rule is judged, never rewritten.
data class ReminderRule(val subject: ReminderSubject, val metricId: String, val mode: ReminderMode,
    val threshold: Int? = null, val enabled: Boolean = true) {
    init {
        require(metricId.isNotBlank() && metricId.length <= 80)
        when (mode) {
            ReminderMode.FULL, ReminderMode.EVENT -> require(threshold == null)
            ReminderMode.PERCENT -> require(threshold != null && threshold in ReminderCapabilities.PERCENT_RANGE)
            ReminderMode.VALUE -> require(threshold != null && threshold >= ReminderCapabilities.MIN_VALUE)
        }
    }
}
