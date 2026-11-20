package cafe.miafetta.tsuki.core.reminder

// Whether a rule may be saved.
enum class RuleCheck {
    VALID,
    // The metric has no capability entry, so it offers no rules.
    UNKNOWN_METRIC,
    // The metric does not offer the mode.
    UNSUPPORTED_MODE,
    // The mode needs an effective limit that the metric does not have now.
    NEEDS_LIMIT,
    // The value threshold is above the metric's current limit.
    ABOVE_LIMIT,
}
