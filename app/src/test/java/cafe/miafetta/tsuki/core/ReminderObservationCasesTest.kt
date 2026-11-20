package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.EventState
import cafe.miafetta.tsuki.core.reminder.ObservationState
import cafe.miafetta.tsuki.core.reminder.ReminderObservation
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.ArknightsParser
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// Constructed data: states the redacted samples do not have (a trainee in training, recruitment fully complete or
// idle, empty or unknown capacities, full drones), built either as snapshots directly or by editing the player sample
// before parsing it. Every case here is constructed.
class ReminderObservationCasesTest {
    private val now = 1_700_000_000_000L
    private val subject = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")

    private fun read(snapshot: WidgetSnapshot, metricId: String): ReminderObservation =
        requireNotNull(ReminderObservations.from(subject, metricId, snapshot, true, arknightsSource = true)) { metricId }

    private fun snapshot(vararg metrics: WidgetMetric, training: WidgetTraining = WidgetTraining()) =
        WidgetSnapshot("角色", listOf(WidgetMetric("理智", 90, 135, now + 60_000)) + metrics, now, training = training)

    private fun room(training: WidgetTraining) = snapshot(WidgetMetric("训练室", 0, 1, training.endsAt, MetricTimer.NEXT_COMPLETION,
        text = "测试干员，3技能，${training.status(now)}"), training = training)

    private fun player() = JSONObject(requireNotNull(javaClass.getResource("/skland/player-redacted.json")).readText())

    @Test fun constructedFullDronesAreFullAtFetchedAt() {
        val full = read(snapshot(WidgetMetric("无人机", 200, 200, 0)), "arknights.drones")
        assertEquals(200, full.value)
        assertEquals(200, full.limit)
        assertEquals(now, full.fullAt)
        assertNull(read(snapshot(WidgetMetric("无人机", 50, 200, null)), "arknights.drones").fullAt)
    }

    @Test fun constructedRunningAndCompleteRoundsAreEventsOfTheirTraineeAndSkill() {
        val running = read(room(WidgetTraining(TrainingState.TRAINING, "char_test_trainee", "测试干员", 2, now + 3_600_000)),
            "arknights.training")
        assertEquals(EventState.RUNNING, running.event)
        assertEquals("char_test_trainee/2", running.round)
        assertEquals(now + 3_600_000, running.fullAt)
        assertEquals(0, running.value)
        assertNull(running.limit)
        assertEquals(ObservationState.VALID, running.state)
        val complete = read(room(WidgetTraining(TrainingState.COMPLETE, "char_test_trainee", "测试干员", 2, now)), "arknights.training")
        assertEquals(EventState.COMPLETE, complete.event)
        assertEquals("char_test_trainee/2", complete.round)
        assertEquals(now, complete.fullAt)
    }

    @Test fun constructedUnknownAndLegacyPausedTrainingAreUnknownObservations() {
        // Speed 0 with time left, as the parser now reads it.
        val data = player()
        data.getJSONObject("building").getJSONObject("training")
            .put("trainee", JSONObject().put("charId", "char_test_trainee").put("targetSkill", 2))
            .put("remainSecs", 3600).put("lastUpdateTime", now / 1000 - 600).put("speed", 0)
        val parsed = read(ArknightsParser.snapshot(data, "000000001", now), "arknights.training")
        // PAUSED as a snapshot of an earlier version reads back.
        val legacy = read(room(WidgetTraining(TrainingState.PAUSED, "char_test_trainee", "测试干员", 2, remainingSecs = 3600)),
            "arknights.training")
        for (unknown in listOf(parsed, legacy)) {
            assertEquals(ObservationState.UNKNOWN, unknown.state)
            assertEquals(EventState.UNKNOWN, unknown.event)
            assertNull(unknown.fullAt)
            assertEquals("char_test_trainee/2", unknown.round)
        }
    }

    @Test fun constructedRecruitIsFullOnceEverySlotInUseIsComplete() {
        // Two slots complete, two idle, none running: full, with the complete slots as the limit built for it.
        val complete = read(snapshot(WidgetMetric("公开招募", 2, 4, null, MetricTimer.NEXT_COMPLETION)), "arknights.recruit")
        assertEquals(2, complete.value)
        assertEquals(2, complete.limit)
        assertEquals(now, complete.fullAt)
        assertEquals(ObservationState.VALID, complete.state)
        // Every slot idle, or no slots at all ("0/0"): nothing in use.
        val idle = read(snapshot(WidgetMetric("公开招募", 0, 4, null, MetricTimer.NEXT_COMPLETION)), "arknights.recruit")
        val none = read(snapshot(WidgetMetric("公开招募", 0, null, null, MetricTimer.NEXT_COMPLETION, "0/0")), "arknights.recruit")
        for (empty in listOf(idle, none)) {
            assertEquals(ObservationState.NO_OBJECT, empty.state)
            assertEquals(0, empty.limit)
            assertNull(empty.fullAt)
        }
    }

    @Test fun constructedEmptyBuildingsAreNoObjectAndAnUnknownCapacityHasNoLimit() {
        val data = player()
        data.put("recruit", JSONArray())
        val building = data.getJSONObject("building")
        building.put("tradings", JSONArray()).put("manufactures", JSONArray().put(JSONObject().put("formulaId", "").put("complete", 0)))
        val empty = ArknightsParser.snapshot(data, "000000001", now)
        for (metricId in listOf("arknights.recruit", "arknights.trading", "arknights.manufacture")) {
            val observation = read(empty, metricId)
            assertEquals(metricId, ObservationState.NO_OBJECT, observation.state)
            assertEquals(metricId, 0, observation.limit)
            assertNull(metricId, observation.nextAt)
        }
        // Products without a formula: "5/未知" keeps a valid value without a limit, so only value rules apply.
        building.getJSONArray("manufactures").getJSONObject(0).put("complete", 5)
        val unknown = read(ArknightsParser.snapshot(data, "000000001", now), "arknights.manufacture")
        assertEquals(5, unknown.value)
        assertNull(unknown.limit)
        assertEquals(ObservationState.VALID, unknown.state)
    }

    @Test fun constructedTradingKeepsTheNextOrderApartFromBeingFull() {
        val trading = read(snapshot(WidgetMetric("订单进度", 7, 24, now + 600_000, MetricTimer.NEXT_COMPLETION, estimated = true)),
            "arknights.trading")
        assertEquals(now + 600_000, trading.nextAt)
        assertNull(trading.fullAt)
        assertTrue(trading.estimated)
        val stocked = read(snapshot(WidgetMetric("订单进度", 3, null, null, MetricTimer.NEXT_COMPLETION, "3/0")), "arknights.trading")
        assertEquals(ObservationState.NO_OBJECT, stocked.state)
        assertEquals(3, stocked.value)
    }
}
