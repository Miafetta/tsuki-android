package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderDecision
import cafe.miafetta.tsuki.core.reminder.ReminderEvaluator
import cafe.miafetta.tsuki.core.reminder.ReminderNotice
import cafe.miafetta.tsuki.core.reminder.ReminderObservation
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSchedule
import cafe.miafetta.tsuki.core.reminder.ReminderStatus
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.RuleCheck
import cafe.miafetta.tsuki.core.reminder.TriggerRecord

// Reminder checking without Android: which rules to judge and what they decide, which notices may be sent, and when
// each role wants its next request. ReminderChecks applies it to the reminder file; the widget process only fetches,
// posts and schedules (widget/WidgetRuntimeReminderChecks.kt).
internal object ReminderPlanner {
    // The enabled rules of [subject] judged with [observe]; nothing while the master switch is off. Rules belong to
    // roles, not widgets, so a role is judged once however many widgets show it.
    fun judge(data: ReminderData, subject: ReminderSubject, observe: (ReminderRule) -> ReminderObservation?,
        now: Long): List<ReminderJudgement> {
        if (!data.enabled) return emptyList()
        return data.entries.filter { it.rule.subject == subject && it.rule.enabled }
            .map { ReminderJudgement(it, decide(it, observe(it.rule), now)) }
    }

    // Keeps the judged records through [write] (ReminderStore.writeRecords) and returns the notices to send: only those
    // whose own record was written, so that a rule edited or removed while it was judged sends nothing for its old
    // state. Records without a notice go in one write; unchanged records are not written.
    fun commit(judged: List<ReminderJudgement>, write: (Map<ReminderEntry, TriggerRecord>) -> Int): List<ReminderNotice> {
        val quiet = judged.filter { it.decision.notice == null && it.decision.record != it.entry.record }
        if (quiet.isNotEmpty()) write(quiet.associate { it.entry to it.decision.record })
        return judged.mapNotNull { judgement ->
            judgement.decision.notice?.takeIf { write(mapOf(judgement.entry to judgement.decision.record)) == 1 }
        }
    }

    // When each role wants its next check. Nothing is planned while the master switch is off or notifications cannot be
    // shown at all; otherwise every role of a stored account ([roles]) with an enabled rule, except roles of a game
    // whose channel is off.
    fun plan(data: ReminderData, roles: Map<ReminderSubject, ReminderRoleInput>, delivery: ReminderDelivery,
        now: Long): ReminderPlan {
        if (!data.enabled || !delivery.notificationsEnabled) return ReminderPlan()
        val enabled = data.entries.filter { it.rule.enabled && it.rule.subject.providerId !in delivery.blockedProviders }
        return ReminderPlan(enabled.groupBy { it.rule.subject }.mapNotNull { (subject, entries) ->
            roles[subject]?.let { times(subject, entries, data.checks[subject] ?: ReminderCheckTimes(), it, now) }
        })
    }

    // The request time is ReminderSchedule.nextCheck over the rules' expected times (with the 30-minute interval in it),
    // pushed back by a failure backoff and by the account's rate limit. A rule the cache already shows met by known
    // times wants a request now as well: fresh data confirms it (or shows it was spent meanwhile), and only a request
    // that fails leaves it to the cache (core/reminder ReminderEvaluator). When the request has to wait, such a rule
    // is judged from the cache at its expected time instead, without a request.
    private fun times(subject: ReminderSubject, entries: List<ReminderEntry>, checks: ReminderCheckTimes,
        input: ReminderRoleInput, now: Long): ReminderRoleTimes {
        val decisions = entries.map { it to decide(it, input.observe(it.rule), now) }
        val expected = decisions.map { (entry, decision) ->
            if (awaitsFirstCheck(entry) || decision.notify) now else decision.expectedAt
        }
        val wanted = ReminderSchedule.nextCheck(expected, checks.lastCalibrationAt, checks.lastRequestAt, now) ?: now
        val requestAt = maxOf(wanted, retryAfter(checks) ?: now, input.retryAt)
        val judgeAt = decisions.mapNotNull { (_, decision) -> judgeAt(decision, now) }.minOrNull()
        return ReminderRoleTimes(subject, requestAt, judgeAt?.takeIf { it < requestAt })
    }

    // After a failed reminder request (one with no new data since: lastRequestAt after lastCalibrationAt), the next one
    // waits as long as the role had then gone without new data, at least the request interval and at most the
    // calibration period. The wait doubles with every failure in a row without a count of failures: a failure 40
    // minutes after the last data waits 40 minutes, the next one 80. A role never calibrated has no such span: after a
    // failed request it waits the whole calibration period, so that a role whose data never comes (an expired Cred, a
    // role gone from the game) is not requested every 30 minutes for good. Null when the last request did not fail.
    fun retryAfter(checks: ReminderCheckTimes): Long? {
        val requested = checks.lastRequestAt ?: return null
        val calibrated = checks.lastCalibrationAt ?: return requested + ReminderSchedule.CALIBRATION_PERIOD_MILLIS
        if (calibrated >= requested) return null
        val outage = requested - calibrated
        return requested + outage.coerceIn(ReminderSchedule.MIN_REQUEST_INTERVAL_MILLIS, ReminderSchedule.CALIBRATION_PERIOD_MILLIS)
    }

    // A rule saved, changed or started over (the master switch turning on starts every rule over) and not judged since
    // wants data now, so that a rule already met reports on its first data rather than at the next calibration. Any
    // judgement ends it, with data or without. A rule whose metric or mode is not offered is never judged and never
    // waits, or its role would be requested at every interval.
    private fun awaitsFirstCheck(entry: ReminderEntry) =
        ReminderCapabilities.check(entry.rule) == RuleCheck.VALID && !entry.record.keptFor(entry.rule)

    // What cached data alone can still do for a rule: report now, or at the expected time when the rule may be worked
    // out from known times (the evaluator says NOT_MET without fresh data only then).
    private fun judgeAt(decision: ReminderDecision, now: Long): Long? = when {
        decision.notify -> now
        decision.status == ReminderStatus.NOT_MET -> decision.expectedAt?.takeIf { it > now }
        else -> null
    }

    private fun decide(entry: ReminderEntry, observation: ReminderObservation?, now: Long) =
        ReminderEvaluator.evaluate(entry.rule, ReminderCapabilities[entry.rule.metricId], observation, entry.record, now)
}
