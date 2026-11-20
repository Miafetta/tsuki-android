package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderObservation
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSchedule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Judging against the reminder file: calibration on new data, a reminder job's start and end, which notices may be
// sent, and that a role is judged once whatever shows it.
class ReminderChecksTest {
    @get:Rule val directory = TemporaryFolder()

    private val now = 1_793_956_830_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")
    private val sanityFull = ReminderRule(arknights, "arknights.sanity", FULL)
    private val endfieldPercent = ReminderRule(endfield, "endfield.sanity", PERCENT, 80)

    private val store by lazy { ReminderStore(File(directory.root, "reminders/v1.json")) }
    private val checks by lazy { ReminderChecks(store) }

    private fun saved(vararg rules: ReminderRule) = store.apply {
        setEnabled(true)
        rules.forEach(::save)
    }

    private fun sanity(value: Int, fullAt: Long?, fetchedAt: Long, fresh: Boolean, subject: ReminderSubject = arknights,
        limit: Int = 135) = ReminderObservation(subject, if (subject == arknights) "arknights.sanity" else "endfield.sanity",
        value, fetchedAt, fresh, limit, fullAt = fullAt)

    private fun cache(observation: ReminderObservation?) = ReminderRoleInput { observation?.copy(fresh = false) }

    @Test fun newDataReportsAMetRuleOnceAndCountsAsACalibration() {
        saved(sanityFull, endfieldPercent)
        val full = sanity(135, now - minute, now - 10_000, fresh = true)
        val notices = checks.calibrate(arknights, now - 10_000, { full }, now)!!
        assertEquals(listOf("理智已满"), notices.map { it.body })
        assertFalse(notices.single().estimated)
        assertEquals(ReminderCheckTimes(lastCalibrationAt = now - 10_000), store.checks(arknights))
        assertTrue(store.read().entry(arknights, "arknights.sanity")!!.record.fired)
        // The same data again, as a second widget of the role would bring it: judged, but nothing more to send.
        assertEquals(emptyList<Any>(), checks.calibrate(arknights, now - 10_000, { full }, now + minute))
        // An older snapshot judged late does not move the calibration back.
        checks.calibrate(arknights, now - hour, { full.copy(fetchedAt = now - hour, dataAt = now - hour) }, now + minute)
        assertEquals(now - 10_000, store.checks(arknights).lastCalibrationAt)
        // The Endfield role was not judged, and a role without enabled rules has nothing to calibrate.
        assertEquals(ReminderCheckTimes(), store.checks(endfield))
        assertNull(checks.calibrate(ReminderSubject(SklandArknightsAdapter.ID, "account-b", "000000002"), now, { null }, now))
        store.setEnabled(false)
        assertNull(checks.calibrate(arknights, now, { full }, now))
    }

    @Test fun aJobRequestsWhatIsDueAndJudgesFailuresFromTheCacheWithoutFreshData() {
        saved(sanityFull)
        // The cache shows sanity full, but the rule has never judged fresh data: it must not report from the cache.
        val cached = sanity(135, now - hour, now - 2 * hour, fresh = false)
        val roles = mapOf(arknights to cache(cached))
        assertEquals(listOf(arknights), checks.due(roles, ReminderDelivery(), now))
        assertTrue(checks.requesting(arknights, now))
        assertEquals(ReminderCheckTimes(lastRequestAt = now), store.checks(arknights))
        // The interval holds at the request itself, whatever the planning said.
        assertFalse(checks.requesting(arknights, now + 29 * minute))
        assertEquals(ReminderCheckTimes(lastRequestAt = now), store.checks(arknights))
        assertEquals(emptyList<Any>(), checks.finish(roles, ReminderDelivery(), mapOf(arknights to now), emptySet(), now + 5_000))
        assertEquals(TriggerRecord(FULL), store.read().entries.single().record)
        // The failed request counts: nothing is due again within 30 minutes, and a role that has never had data waits
        // the whole calibration period after a failure (ReminderPlanner.retryAfter).
        assertEquals(emptyList<ReminderSubject>(), checks.due(roles, ReminderDelivery(), now + 29 * minute))
        assertEquals(now + ReminderSchedule.CALIBRATION_PERIOD_MILLIS, checks.wakeAt(roles, ReminderDelivery(), now + minute))
    }

    @Test fun ruleWorkedOutFromKnownTimesReportsOnTimeFromTheCache() {
        saved(sanityFull)
        // Fresh data an hour ago: 5 short of full, full in 30 minutes.
        val seen = sanity(130, now + 30 * minute, now - hour, fresh = true)
        assertEquals(emptyList<Any>(), checks.calibrate(arknights, now - hour, { seen }, now - hour))
        val roles = mapOf(arknights to cache(seen))
        // A request at the full time fails (offline): the cache works it out and reports, marked as estimated.
        val at = now + 30 * minute
        assertEquals(listOf(arknights), checks.due(roles, ReminderDelivery(), at))
        checks.requesting(arknights, at)
        val notices = checks.finish(roles, ReminderDelivery(), mapOf(arknights to at), emptySet(), at + 5_000)
        assertEquals(listOf("理智已满"), notices.map { it.body })
        assertTrue(notices.single().estimated)
        // The cache says nothing new afterwards.
        assertEquals(emptyList<Any>(), checks.finish(roles, ReminderDelivery(), emptyMap(), emptySet(), at + hour))
    }

    @Test fun aRequestAnsweredFromTheRequestWindowCountsAsACalibration() {
        saved(sanityFull)
        store.writeChecks(arknights, ReminderCheckTimes(now - 10_000, now - hour))
        // The adapter answered from its 20-second window: data, but no new snapshot to calibrate with.
        checks.requesting(arknights, now)
        checks.finish(mapOf(arknights to cache(null)), ReminderDelivery(), mapOf(arknights to now), setOf(arknights), now + 1_000)
        assertEquals(ReminderCheckTimes(now, now), store.checks(arknights))
        assertNull(ReminderPlanner.retryAfter(store.checks(arknights)))
    }

    @Test fun aRuleChangedWhileItWasJudgedSendsNothing() {
        saved(sanityFull)
        val full = sanity(135, 0, now, fresh = true)
        val judged = ReminderPlanner.judge(store.read(), arknights, { full }, now)
        assertTrue(judged.single().decision.notify)
        // The user switches the rule to a percentage before the judgement is written.
        store.save(ReminderRule(arknights, "arknights.sanity", PERCENT, 90))
        assertEquals(emptyList<Any>(), ReminderPlanner.commit(judged, store::writeRecords))
        assertFalse(store.read().entries.single().record.fired)
        // The master switch turned off and on again starts the rule over: a judgement from before is not written either.
        checks.calibrate(arknights, now, { sanity(60, now + 7 * hour, now, fresh = true) }, now)
        val later = full.copy(fetchedAt = now + minute, dataAt = now + minute)
        val again = ReminderPlanner.judge(store.read(), arknights, { later }, now + minute)
        assertTrue(again.single().decision.notify)
        store.setEnabled(false)
        store.setEnabled(true)
        assertEquals(emptyList<Any>(), ReminderPlanner.commit(again, store::writeRecords))
        assertEquals(TriggerRecord(), store.read().entries.single().record)
    }

    @Test fun aRoleIsJudgedOnceForAllItsRulesAndWidgets() {
        saved(sanityFull, ReminderRule(arknights, "arknights.drones", PERCENT, 50), endfieldPercent)
        val full = sanity(135, 0, now, fresh = true)
        val observe = { rule: ReminderRule -> if (rule.metricId == "arknights.sanity") full else null }
        val judged = ReminderPlanner.judge(store.read(), arknights, observe, now)
        // One judgement per enabled rule of the role, none for the other role; a rule is not tied to any widget.
        assertEquals(listOf("arknights.sanity", "arknights.drones"), judged.map { it.entry.rule.metricId })
        assertEquals(1, ReminderPlanner.commit(judged, store::writeRecords).size)
        // Two widgets refreshing the role one after the other send one notice.
        assertEquals(emptyList<Any>(), checks.calibrate(arknights, now, observe, now + 1_000))
        assertTrue(ReminderPlanner.judge(store.read().copy(enabled = false), arknights, observe, now).isEmpty())
    }
}
