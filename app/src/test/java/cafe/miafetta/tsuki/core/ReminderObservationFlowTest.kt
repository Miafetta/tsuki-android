package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderDecision
import cafe.miafetta.tsuki.core.reminder.ReminderEvaluator
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderStatus
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOTIFIED
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOT_MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.UNAVAILABLE
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord
import cafe.miafetta.tsuki.providers.ArknightsParser
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// End to end: snapshots read as observations and judged by ReminderEvaluator, with the record kept between checks the
// way the reminder checks keep it. Recruitment starts from the redacted player sample, whose later states are
// constructed by editing it; every training state is constructed (neither sample has a trainee).
class ReminderObservationFlowTest {
    private val serverSeconds = 1_700_000_000L
    private val subject = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val minute = 60_000L
    private val hour = 3_600_000L

    // A check: the snapshot, whether it was obtained just now, and the local time it is judged at.
    private class Check(val snapshot: WidgetSnapshot, val fresh: Boolean, val at: Long)

    private fun ReminderRule.walk(vararg checks: Check): List<ReminderDecision> {
        var record = TriggerRecord()
        return checks.map { check ->
            val observation = ReminderObservations.from(subject, metricId, check.snapshot, check.fresh, arknightsSource = true)
            ReminderEvaluator.evaluate(this, ReminderCapabilities[metricId], observation, record, check.at).also { record = it.record }
        }
    }

    private fun assertStatuses(expected: List<ReminderStatus>, decisions: List<ReminderDecision>) =
        assertEquals(expected, decisions.map { it.status })

    // The player sample as received [elapsed] seconds after its own time, with its recruitment slots replaced by
    // [slots] (state to finish time in server seconds) when given. The local clock equals the server's here.
    private fun recruitment(elapsed: Long, vararg slots: Pair<Int, Long>): WidgetSnapshot {
        val data = JSONObject(requireNotNull(javaClass.getResource("/skland/player-redacted.json")).readText())
        data.put("currentTs", serverSeconds + elapsed)
        if (slots.isNotEmpty()) data.put("recruit", org.json.JSONArray(slots.map { (state, finish) ->
            JSONObject().put("state", state).put("finishTs", finish)
        }))
        return ArknightsParser.snapshot(data, "000000001", (serverSeconds + elapsed) * 1000)
    }

    @Test fun recruitmentRemindsOnceWhenTheLastRunningSlotCompletes() {
        val rule = ReminderRule(subject, "arknights.recruit", ReminderMode.FULL)
        val start = serverSeconds * 1000
        // The sample: one slot running until +300 s, one complete, two idle. Then fresh data after it finished, and
        // again later; then a new recruitment started after collecting (constructed), and finally every slot idle.
        val decisions = rule.walk(Check(recruitment(0), true, start), Check(recruitment(400), true, start + 400_000),
            Check(recruitment(1_000), true, start + 1_000_000),
            Check(recruitment(1_200, 1 to -1L, 2 to serverSeconds + 9_000, 1 to -1L, 1 to -1L), true, start + 1_200_000),
            Check(recruitment(1_500, 1 to -1L, 1 to -1L, 1 to -1L, 1 to -1L), true, start + 1_500_000))
        assertStatuses(listOf(NOT_MET, NOTIFIED, MET, NOT_MET, UNAVAILABLE), decisions)
        assertEquals(start + 300_000, decisions[0].expectedAt)
        val notice = requireNotNull(decisions[1].notice)
        assertEquals("公开招募已全部完成", notice.body)
        assertFalse(notice.estimated)
        // Fresh data with a slot running again re-arms the rule; idle slots leave the record as it was.
        assertFalse(decisions[3].record.fired)
        assertEquals(decisions[3].record, decisions[4].record)
    }

    @Test fun recruitmentWorkedOutFromItsFinishTimeIsNotRepeatedByTheFreshData() {
        val rule = ReminderRule(subject, "arknights.recruit", ReminderMode.FULL)
        val start = serverSeconds * 1000
        val first = recruitment(0)
        // A check without fresh data after the finish time reports from the time; the fresh data then only agrees.
        val decisions = rule.walk(Check(first, true, start), Check(first, false, start + 301_000),
            Check(recruitment(400), true, start + 400_000))
        assertStatuses(listOf(NOT_MET, NOTIFIED, MET), decisions)
        assertTrue(requireNotNull(decisions[1].notice).estimated)
    }

    // A constructed training room snapshot at local time [at] (the server's clock is the same here).
    private fun room(training: WidgetTraining, at: Long) = WidgetSnapshot("角色", listOf(WidgetMetric("理智", 90, 135, at),
        WidgetMetric("训练室", if (training.state == TrainingState.COMPLETE) 1 else 0, 1, training.endsAt, MetricTimer.NEXT_COMPLETION,
            text = "测试干员，3技能，${training.status(at)}")), at, training = training)

    @Test fun constructedTrainingRemindsOncePerRound() {
        val rule = ReminderRule(subject, "arknights.training", ReminderMode.EVENT)
        val t = serverSeconds * 1000
        val running = WidgetTraining(TrainingState.TRAINING, "char_test_trainee", "测试干员", 2, t + hour)
        fun complete(at: Long, id: String = "char_test_trainee", skill: Int = 2) =
            room(WidgetTraining(TrainingState.COMPLETE, id, "测试干员", skill, at), at)
        val done = t + hour + 30 * minute
        val decisions = rule.walk(Check(room(running, t), true, t), Check(complete(done), true, done),
            Check(complete(t + 3 * hour), true, t + 3 * hour),
            // A PAUSED training of an earlier version's cache says nothing; the record stays.
            Check(room(WidgetTraining(TrainingState.PAUSED, "char_test_trainee", "测试干员", 2, remainingSecs = 600), t + 4 * hour),
                true, t + 4 * hour),
            Check(complete(t + 5 * hour, "char_test_other", 1), true, t + 5 * hour))
        assertStatuses(listOf(NOT_MET, NOTIFIED, MET, UNAVAILABLE, NOTIFIED), decisions)
        assertEquals(t + hour, decisions[0].expectedAt)
        assertEquals("训练室专精训练已完成", requireNotNull(decisions[1].notice).body)
        assertEquals("char_test_trainee/2", decisions[1].notice?.round)
        assertEquals(decisions[2].record, decisions[3].record)
        assertEquals("char_test_other/1", decisions[4].notice?.round)
    }

    @Test fun constructedTrainingWorkedOutFromItsEndTimeIsNotRepeatedByTheFreshData() {
        val rule = ReminderRule(subject, "arknights.training", ReminderMode.EVENT)
        val t = serverSeconds * 1000
        val first = room(WidgetTraining(TrainingState.TRAINING, "char_test_trainee", "测试干员", 2, t + hour), t)
        val done = t + hour + minute
        val decisions = rule.walk(Check(first, true, t), Check(first, false, done),
            Check(room(WidgetTraining(TrainingState.COMPLETE, "char_test_trainee", "测试干员", 2, done), done), true, done))
        assertStatuses(listOf(NOT_MET, NOTIFIED, MET), decisions)
        assertTrue(requireNotNull(decisions[1].notice).estimated)
    }
}
