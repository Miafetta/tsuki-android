package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.assertEquals

// Shared builders of the reminder tests. Times are local epoch milliseconds.
abstract class ReminderFixture {
    protected val now = 1_793_956_830_000L
    protected val second = 1_000L
    protected val minute = 60_000L
    protected val hour = 3_600_000L
    protected val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    protected val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")

    protected fun subjectOf(metricId: String) = if (metricId.startsWith("endfield.")) endfield else arknights

    protected fun rule(metricId: String, mode: ReminderMode, threshold: Int? = null, enabled: Boolean = true) =
        ReminderRule(subjectOf(metricId), metricId, mode, threshold, enabled)

    protected fun observe(metricId: String, value: Int, limit: Int?, fresh: Boolean = true, fetchedAt: Long = now,
        fullAt: Long? = null, nextAt: Long? = null, dataAt: Long = fetchedAt, state: ObservationState = ObservationState.VALID,
        round: String? = null, event: EventState? = null) =
        ReminderObservation(subjectOf(metricId), metricId, value, fetchedAt, fresh, limit, state, fullAt = fullAt,
            nextAt = nextAt, dataAt = dataAt, round = round, event = event)

    protected fun training(event: EventState, fresh: Boolean = true, fullAt: Long? = null, round: String? = ROUND_A,
        fetchedAt: Long = now) = observe("arknights.training", 0, null, fresh, fetchedAt, fullAt, round = round, event = event)

    protected fun judge(rule: ReminderRule, observation: ReminderObservation?, record: TriggerRecord = TriggerRecord(),
        at: Long = now) = ReminderEvaluator.evaluate(rule, ReminderCapabilities[rule.metricId], observation, record, at)

    protected fun expected(rule: ReminderRule, observation: ReminderObservation, at: Long = now) =
        ReminderSchedule.expectedAt(rule, ReminderCapabilities[rule.metricId]!!, observation, at)

    // The record of [rule] after it judged [observation] as fresh data without reporting: the baseline that lets later
    // checks without fresh data work the rule out from known times.
    protected fun baseline(rule: ReminderRule, observation: ReminderObservation) =
        TriggerRecord(rule.mode, rule.threshold, dataAt = observation.dataAt)

    // Judges the observations in turn, each at its time, keeping the record between them like the scheduler does.
    protected fun ReminderRule.walk(vararg steps: Pair<ReminderObservation?, Long>,
        record: TriggerRecord = TriggerRecord()): List<ReminderDecision> {
        var kept = record
        return steps.map { (observation, at) -> judge(this, observation, kept, at).also { kept = it.record } }
    }

    protected fun assertStatuses(expected: List<ReminderStatus>, decisions: List<ReminderDecision>) {
        assertEquals(expected, decisions.map { it.status })
        assertEquals(expected.map { it == ReminderStatus.NOTIFIED }, decisions.map { it.notify })
    }

    protected companion object {
        const val ROUND_A = "char_002_amiya/2"
        const val ROUND_B = "char_017_huang/1"
    }
}
