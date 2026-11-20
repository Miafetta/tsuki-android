package cafe.miafetta.tsuki.core.reminder

// When to check a role next. Backing off after failures is the scheduler's own concern and not part of this.
object ReminderSchedule {
    // Hard limit: reminder jobs never request the same role twice within 30 minutes (docs/DEVELOPMENT_CONSTRAINTS.md
    // §2). It applies to every request, retries included.
    const val MIN_REQUEST_INTERVAL_MILLIS = 30 * 60_000L

    // Target period of the low-frequency calibration of a role with reminders. An initial value, to be settled by
    // observing real devices; it is only the interval asked for, the system may defer jobs well beyond it.
    const val CALIBRATION_PERIOD_MILLIS = 3 * 60 * 60_000L

    // When [rule] wants its role checked, judged from [observation]: the expected time the rule is met. Null when the
    // rule is met already, cannot be judged, or its metric gives no time to plan by. The time may be in the past when a
    // rule that needs fresh data is already expected to be met; the role is then due as soon as the interval allows.
    fun expectedAt(rule: ReminderRule, capability: ReminderCapability, observation: ReminderObservation, now: Long): Long? {
        require(capability.metricId == rule.metricId && observation.metricId == rule.metricId && observation.subject == rule.subject)
        if (!rule.enabled || rule.mode !in capability.modes) return null
        if (ReminderCondition.blocker(rule, observation) != null) return null
        if (ReminderCondition.observedMet(rule, observation)) return null
        if (ReminderCondition.projectedMet(rule, capability, observation, now)) return null
        val after = observation.fetchedAt
        return when (capability.timing) {
            ReminderTiming.NONE -> null
            ReminderTiming.NEXT_OUTPUT -> observation.nextAt?.takeIf { it > after }
            ReminderTiming.REACH_TIME -> ReminderCondition.reachAt(rule, capability, observation)?.let { reach ->
                // An estimate the data has already overtaken falls back to the full time, which bounds every threshold.
                if (reach > after) reach else observation.fullAt?.takeIf { it > after }
            }
        }
    }

    // The next check of one role: max(last request + 30 minutes, min(expected times, last calibration + period)), and
    // never before [now]. [expected] holds one entry per enabled rule of the role (null when the rule has no time of
    // its own); without enabled rules the role is not scheduled at all. A role never calibrated is due now.
    fun nextCheck(expected: Collection<Long?>, lastCalibrationAt: Long?, lastRequestAt: Long?, now: Long): Long? {
        if (expected.isEmpty()) return null
        val calibration = lastCalibrationAt?.let { it + CALIBRATION_PERIOD_MILLIS } ?: now
        val wanted = minOf(expected.filterNotNull().minOrNull() ?: calibration, calibration)
        val earliest = lastRequestAt?.let { it + MIN_REQUEST_INTERVAL_MILLIS } ?: now
        return maxOf(wanted, earliest, now)
    }
}
