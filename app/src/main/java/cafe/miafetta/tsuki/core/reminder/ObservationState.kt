package cafe.miafetta.tsuki.core.reminder

// Whether a metric could be read. Only VALID takes part in judging; an unknown, locked or empty (no object, such as
// "0/0") metric never counts as met or complete and leaves the trigger record as it was.
enum class ObservationState { VALID, UNKNOWN, LOCKED, NO_OBJECT }
