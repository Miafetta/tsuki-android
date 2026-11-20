package cafe.miafetta.tsuki.core.reminder

// State of an event metric's current round. UNKNOWN never triggers and never re-arms a rule.
enum class EventState { IDLE, RUNNING, COMPLETE, UNKNOWN }
