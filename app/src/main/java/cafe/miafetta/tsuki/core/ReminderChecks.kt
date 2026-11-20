package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderNotice
import cafe.miafetta.tsuki.core.reminder.ReminderObservation
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSchedule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject

// The steps of reminder checking that read and write the reminder file (ReminderPlanner applied to ReminderStore). The
// caller holds the runtime lock around each step, as every other reminder operation does, and posts the returned
// notices and reschedules after releasing it (widget/WidgetRuntimeReminderChecks.kt). Every step throws what
// ReminderStore throws.
internal class ReminderChecks(private val store: ReminderStore) {
    // New data of [subject] from any refresh path, obtained at [fetchedAt]: its enabled rules are judged as fresh data
    // and the role counts as calibrated. Null when the role has nothing to judge (no enabled rule, or reminders off), so
    // that the caller has nothing to reschedule.
    fun calibrate(subject: ReminderSubject, fetchedAt: Long, observe: (ReminderRule) -> ReminderObservation?,
        now: Long): List<ReminderNotice>? {
        val judged = ReminderPlanner.judge(store.read(), subject, observe, now)
        if (judged.isEmpty()) return null
        val notices = ReminderPlanner.commit(judged, store::writeRecords)
        calibrated(subject, fetchedAt)
        return notices
    }

    // The roles a reminder job requests now.
    fun due(roles: Map<ReminderSubject, ReminderRoleInput>, delivery: ReminderDelivery, now: Long) =
        ReminderPlanner.plan(store.read(), roles, delivery, now).requests(now)

    // Whether [subject] may be requested at [at], recording the request if so. The 30-minute interval is a hard limit
    // (docs/DEVELOPMENT_CONSTRAINTS.md §2), so it is checked here again rather than trusted to planning; the time is
    // written before the request goes out, so that a request that fails or never returns still counts.
    fun requesting(subject: ReminderSubject, at: Long): Boolean {
        val times = store.checks(subject)
        val last = times.lastRequestAt
        if (last != null && at < last + ReminderSchedule.MIN_REQUEST_INTERVAL_MILLIS) return false
        store.writeChecks(subject, times.copy(lastRequestAt = at))
        return true
    }

    // The end of a reminder job that requested [requests] (role to request time) and got data for [fetched]. New
    // snapshots were judged as they arrived (calibrate). A request answered from the adapter's 20-second window brings
    // no new snapshot, though its answer is data as recent as the window, so a successful request also counts as a
    // calibration at its own time. The roles requested without success, and the roles whose rules can be worked out
    // from known times by now, are judged from their cached data, which is never fresh.
    fun finish(roles: Map<ReminderSubject, ReminderRoleInput>, delivery: ReminderDelivery, requests: Map<ReminderSubject, Long>,
        fetched: Set<ReminderSubject>, now: Long): List<ReminderNotice> {
        fetched.forEach { subject -> requests[subject]?.let { calibrated(subject, it) } }
        val data = store.read()
        val plan = ReminderPlanner.plan(data, roles, delivery, now)
        val failed = plan.roles.map { it.subject }.filter { it in requests && it !in fetched }
        return (failed + plan.judged(now)).distinct().flatMap { subject ->
            ReminderPlanner.commit(ReminderPlanner.judge(data, subject, roles.getValue(subject).observe, now), store::writeRecords)
        }
    }

    // When the reminder job should run next; null: cancel it.
    fun wakeAt(roles: Map<ReminderSubject, ReminderRoleInput>, delivery: ReminderDelivery, now: Long) =
        ReminderPlanner.plan(store.read(), roles, delivery, now).wakeAt

    // Calibration times only move forward: a snapshot judged late does not undo a newer one.
    private fun calibrated(subject: ReminderSubject, at: Long) {
        val times = store.checks(subject)
        val last = times.lastCalibrationAt
        if (last == null || last < at) store.writeChecks(subject, times.copy(lastCalibrationAt = at))
    }
}
