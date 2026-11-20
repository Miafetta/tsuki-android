package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderDecision
import cafe.miafetta.tsuki.core.reminder.ReminderObservation
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject

// When the roles with reminders want their next checks (ReminderPlanner.plan), and the inputs and results of planning
// and judging. Times are local epoch milliseconds.
internal data class ReminderPlan(val roles: List<ReminderRoleTimes> = emptyList()) {
    // When the reminder job runs next; null when nothing is to be checked and the job is cancelled.
    val wakeAt: Long? get() = roles.minOfOrNull { it.wakeAt }

    // The roles to request now.
    fun requests(now: Long) = roles.filter { it.requestAt <= now }.map { it.subject }

    // The roles to judge from their cached data now, without a request.
    fun judged(now: Long) = roles.filter { it.judgeAt != null && it.judgeAt <= now }.map { it.subject }
}

// When one role wants its next reminder request, and, when that is earlier, a judgement from known times without a
// request (null: none before the request).
internal data class ReminderRoleTimes(val subject: ReminderSubject, val requestAt: Long, val judgeAt: Long? = null) {
    val wakeAt get() = judgeAt?.let { minOf(it, requestAt) } ?: requestAt
}

// What reminder checking knows of a role besides the reminder file: when its account's rate limit ends (0: none), and
// its cached data as observations without fresh data (null for a metric without data).
internal class ReminderRoleInput(val retryAt: Long = 0, val observe: (ReminderRule) -> ReminderObservation?)

// Whether notifications can be shown: the app's notifications as a whole, and the games whose channel the user turned
// off (by data source ID). Roles of those games are not checked.
internal data class ReminderDelivery(val notificationsEnabled: Boolean = true, val blockedProviders: Set<String> = emptySet())

// One rule judged: the entry it was judged from, which the record may only replace unchanged, and the decision.
internal data class ReminderJudgement(val entry: ReminderEntry, val decision: ReminderDecision)
