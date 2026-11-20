package cafe.miafetta.tsuki.core.reminder

// Why a decision came out as it did.
enum class ReminderStatus {
    // The rule is off; its record is cleared so that enabling it again starts over.
    DISABLED,
    // The metric has no capability entry or does not offer the rule's mode.
    UNSUPPORTED,
    // Nothing to judge: there is no observation, or no fresh data and the mode may not be judged from known times.
    AWAITING_DATA,
    // Fresh data older than the data the record was last judged with.
    STALE,
    // The observation is unknown, locked, empty or without the limit the mode needs.
    UNAVAILABLE,
    // The value threshold is above the current limit; the rule must be confirmed again and is not rewritten.
    NEEDS_REVIEW,
    NOT_MET,
    // Met and already reported.
    MET,
    // Met and reported now.
    NOTIFIED,
}
