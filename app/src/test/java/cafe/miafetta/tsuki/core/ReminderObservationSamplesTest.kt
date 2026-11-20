package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.EventState
import cafe.miafetta.tsuki.core.reminder.ObservationState
import cafe.miafetta.tsuki.core.reminder.ReminderObservation
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.ArknightsParser
import cafe.miafetta.tsuki.providers.EndfieldParser
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// The reminder metrics as read from the redacted production samples, parsed the way the data sources parse them.
// Arknights snapshots carry no metric IDs, so these are also read by label. The local clock runs two seconds ahead of
// the server's, which keeps fetchedAt (local) and dataAt (server) apart.
class ReminderObservationSamplesTest {
    private val serverNow = 1_700_000_000_000L
    private val received = serverNow + 2_000
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")

    private fun sample(name: String) = JSONObject(requireNotNull(javaClass.getResource("/skland/$name-redacted.json")).readText())
    private fun parsed(name: String) = ArknightsParser.snapshot(sample(name), "000000001", received)

    private fun read(snapshot: WidgetSnapshot, metricId: String): ReminderObservation {
        val arknightsSource = metricId.startsWith("arknights.")
        val observation = ReminderObservations.from(if (arknightsSource) arknights else endfield, metricId, snapshot, true,
            arknightsSource)
        return requireNotNull(observation) { metricId }
    }

    private fun assertRead(observation: ReminderObservation, value: Int, limit: Int?, state: ObservationState = ObservationState.VALID,
        fullAt: Long? = null, nextAt: Long? = null) {
        assertEquals(value, observation.value)
        assertEquals(limit, observation.limit)
        assertEquals(state, observation.state)
        assertEquals(fullAt, observation.fullAt)
        assertEquals(nextAt, observation.nextAt)
    }

    @Test fun dronesRecoverTowardsTheirMaximumWithAnEstimatedValue() {
        val player = read(parsed("player"), "arknights.drones")
        assertRead(player, 108, 200, fullAt = received + 3_300_000)
        assertTrue(player.estimated)
        assertEquals(received, player.fetchedAt)
        assertEquals(serverNow, player.dataAt)
        assertTrue(player.fresh)
        assertRead(read(parsed("production"), "arknights.drones"), 52, 235, fullAt = received + 37_574_000)
    }

    @Test fun dormitoryRestCountsRestedOperatorsAndAnEmptyDormitoryIsNoObject() {
        val production = read(parsed("production"), "arknights.dormitory_rest")
        assertRead(production, 18, 20)
        assertFalse(production.estimated)
        // The player sample has no operator in any dormitory: "0/0".
        assertRead(read(parsed("player"), "arknights.dormitory_rest"), 0, 0, ObservationState.NO_OBJECT)
    }

    @Test fun anEmptyTrainingRoomIsAnIdleEventWithoutARound() {
        // Both samples have no trainee; a running round is only covered by constructed data.
        val idle = read(parsed("player"), "arknights.training")
        assertRead(idle, 0, null)
        assertEquals(EventState.IDLE, idle.event)
        assertNull(idle.round)
        assertEquals(EventState.IDLE, read(parsed("production"), "arknights.training").event)
    }

    @Test fun recruitIsFullOnlyWhenNoSlotInUseIsStillRunning() {
        // One slot running, one complete and two idle: not full until the running slot finishes.
        val player = read(parsed("player"), "arknights.recruit")
        assertRead(player, 1, 2, fullAt = received + 300_000)
        // All four slots running: the latest finish time is when all are complete.
        assertRead(read(parsed("production"), "arknights.recruit"), 0, 1, fullAt = received + 21_715_000)
    }

    @Test fun tradingGivesItsStockCapacityAndNextOrder() {
        assertRead(read(parsed("player"), "arknights.trading"), 3, 24, nextAt = received + 600_000)
        // Both orders are worked out as done, with no time known for the next one.
        val production = read(parsed("production"), "arknights.trading")
        assertRead(production, 2, 20)
        assertTrue(production.estimated)
    }

    @Test fun manufactureGivesItsProductsCapacityAndNextOutput() {
        val player = read(parsed("player"), "arknights.manufacture")
        assertRead(player, 17, 30, nextAt = received + 900_000)
        assertFalse(player.estimated)
    }

    @Test fun endfieldProgressModulesReadAgainstTheirMaximum() {
        val receivedAt = 1_793_956_830_000L
        val card = EndfieldParser.snapshot(sample("endfield-card"), "0000000011", receivedAt)
        assertRead(read(card, "endfield.daily_activation"), 0, 100)
        assertRead(read(card, "endfield.weekly_mission"), 0, 10)
        val pass = read(card, "endfield.pass_level")
        assertRead(pass, 0, 60)
        assertEquals(receivedAt, pass.fetchedAt)
        assertEquals(1_793_956_828_000L, pass.dataAt)
        assertFalse(pass.estimated)
    }
}
