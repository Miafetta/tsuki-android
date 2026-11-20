package cafe.miafetta.tsuki.core.reminder

// A notification to send. The title ("{role}（{game}）") is assembled by the sender, which knows the role and game names;
// [body] is the finished text. [estimated] says the condition was worked out from known times rather than seen in
// fresh data; the text does not change for it, but logs and calibration can tell the two apart.
data class ReminderNotice(
    val subject: ReminderSubject,
    val metricId: String,
    val mode: ReminderMode,
    val threshold: Int?,
    val body: String,
    val estimated: Boolean,
    val round: String? = null,
)
