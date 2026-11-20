package cafe.miafetta.tsuki.core.reminder

// How a metric's next check can be planned ahead. Metrics that cannot are only compared at the role's calibration and
// on successful foreground refreshes.
enum class ReminderTiming {
    // No usable time.
    NONE,
    // At the time the rule is met: the full or completion time, and for lower thresholds the time the recovery model
    // works back to from it.
    REACH_TIME,
    // Once, at the next output. A next output is not the time of being full, so no full time is worked out from it.
    NEXT_OUTPUT,
}
