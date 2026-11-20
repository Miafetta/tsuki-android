package cafe.miafetta.tsuki.core.reminder

// How a rule is met: the metric is full, reaches a percentage of its limit, reaches a value, or (for event metrics)
// the current round completes. Rules are stored by entry name, so entries may only be appended.
enum class ReminderMode { FULL, PERCENT, VALUE, EVENT }
