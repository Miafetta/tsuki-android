package cafe.miafetta.tsuki.core.reminder

// The condition of a rule against one observation, shared by the evaluator and the schedule. "Met" is "reached or
// passed"; percentages are compared in whole numbers so that no floating-point edge decides a boundary.
internal object ReminderCondition {
    // Why [observation] cannot be judged for [rule], or null when it can. Unknown, locked and empty metrics, "0/0", and
    // a missing limit for FULL and PERCENT never take part. A value threshold above the limit needs the user again.
    fun blocker(rule: ReminderRule, observation: ReminderObservation): ReminderStatus? {
        if (observation.state != ObservationState.VALID) return ReminderStatus.UNAVAILABLE
        val limit = observation.limit
        return when (rule.mode) {
            ReminderMode.EVENT ->
                if (observation.event == null || observation.event == EventState.UNKNOWN) ReminderStatus.UNAVAILABLE else null
            ReminderMode.FULL, ReminderMode.PERCENT -> if (limit == null || limit == 0) ReminderStatus.UNAVAILABLE else null
            ReminderMode.VALUE -> when {
                limit == 0 -> ReminderStatus.UNAVAILABLE
                limit != null && threshold(rule) > limit -> ReminderStatus.NEEDS_REVIEW
                else -> null
            }
        }
    }

    // Whether the data itself shows the rule met at fetchedAt. Only for observations without a blocker.
    fun observedMet(rule: ReminderRule, observation: ReminderObservation): Boolean {
        val value = observation.value.toLong()
        val limit = observation.limit?.toLong()?.takeIf { it > 0 }
        val fullBy = observation.fullAt?.let { it <= observation.fetchedAt } == true
        return when (rule.mode) {
            ReminderMode.FULL -> limit != null && (value >= limit || fullBy)
            ReminderMode.PERCENT -> limit != null && value * 100 >= threshold(rule).toLong() * limit
            ReminderMode.VALUE -> value >= threshold(rule)
            ReminderMode.EVENT -> observation.event == EventState.COMPLETE || observation.event == EventState.RUNNING && fullBy
        }
    }

    // Whether the known times say the rule is met by [now]. Only for modes the capability lets be judged that way.
    fun projectedMet(rule: ReminderRule, capability: ReminderCapability, observation: ReminderObservation, now: Long) =
        rule.mode in capability.projected && reachAt(rule, capability, observation)?.let { it <= now } == true

    // When the known times say the rule is met: the full (or completion) time, or for a lower threshold the time the
    // recovery model works back to from it. Null without a full time, or when the model gives none.
    fun reachAt(rule: ReminderRule, capability: ReminderCapability, observation: ReminderObservation): Long? {
        val fullAt = observation.fullAt ?: return null
        if (rule.mode == ReminderMode.FULL || rule.mode == ReminderMode.EVENT) return fullAt
        val limit = observation.limit?.takeIf { it > 0 } ?: return null
        val target = target(rule, limit)
        if (target > limit) return null
        if (observation.value >= target) return observation.fetchedAt
        if (fullAt <= observation.fetchedAt) return fullAt
        return capability.recovery.reachAt(observation.value, limit, target, observation.fetchedAt, fullAt)
    }

    // The smallest value that meets a PERCENT or VALUE rule under [limit]: for a percentage, the first whole value v
    // with v * 100 >= P * limit.
    private fun target(rule: ReminderRule, limit: Int): Int = when (rule.mode) {
        ReminderMode.PERCENT -> ((threshold(rule).toLong() * limit + 99) / 100).toInt()
        else -> threshold(rule)
    }

    private fun threshold(rule: ReminderRule) = requireNotNull(rule.threshold)
}
