package cafe.miafetta.tsuki.core.reminder

// What reminders one metric offers. [name] is the metric's name in reminder settings. [projected] lists the modes that
// may be judged from known times when a check obtains no fresh data; the others are judged on fresh data only, even
// when [timing] uses an estimate to decide when to check.
data class ReminderCapability(
    val metricId: String,
    val name: String,
    val modes: Set<ReminderMode>,
    val projected: Set<ReminderMode>,
    val timing: ReminderTiming,
    val recovery: RecoveryModel,
    val texts: ReminderTexts,
) {
    init {
        require(metricId.isNotBlank() && name.isNotBlank() && modes.isNotEmpty() && modes.containsAll(projected))
        // An event metric has no value to compare, and a value metric no rounds.
        require(ReminderMode.EVENT !in modes || modes.size == 1)
        require(modes.all { texts.body(it, ReminderCapabilities.MIN_VALUE) != null })
    }

    // The modes on offer while the metric's effective limit is [limit] (null when unknown): without a limit there is
    // nothing to be full of or to take a percentage of.
    fun modesFor(limit: Int?): Set<ReminderMode> =
        if (limit != null && limit > 0) modes else modes - setOf(ReminderMode.FULL, ReminderMode.PERCENT)

    // Valid thresholds of [mode] while the effective limit is [limit]; null for modes without a threshold. The upper
    // bound of a value comes from the observed limit, never from a fixed game capacity.
    fun thresholdRange(mode: ReminderMode, limit: Int?): IntRange? = when (mode) {
        ReminderMode.PERCENT -> ReminderCapabilities.PERCENT_RANGE
        ReminderMode.VALUE -> ReminderCapabilities.MIN_VALUE..(limit?.takeIf { it > 0 } ?: Int.MAX_VALUE)
        ReminderMode.FULL, ReminderMode.EVENT -> null
    }
}
