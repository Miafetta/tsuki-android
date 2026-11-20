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

// When roles are checked: the rules' expected times, the calibration period and the 30-minute request interval, the
// backoff after failures and rate limits, judging from known times between requests, and when nothing is scheduled.
class ReminderPlannerTest {
    @get:Rule val directory = TemporaryFolder()

    private val now = 1_793_956_830_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")
    private val sanityFull = ReminderRule(arknights, "arknights.sanity", FULL)
    private val endfieldPercent = ReminderRule(endfield, "endfield.sanity", PERCENT, 80)

    // Arknights sanity as cached at [fetchedAt], full at [fullAt]; dataAt follows fetchedAt.
    private fun sanity(value: Int, fullAt: Long?, fetchedAt: Long = now - hour, subject: ReminderSubject = arknights,
        metricId: String = "arknights.sanity", limit: Int = 135) =
        ReminderObservation(subject, metricId, value, fetchedAt, false, limit, fullAt = fullAt)

    private fun input(retryAt: Long = 0, observe: (ReminderRule) -> ReminderObservation? = { null }) =
        ReminderRoleInput(retryAt, observe)

    // A rule that has judged the fresh data [observation] without reporting.
    private fun judged(rule: ReminderRule, observation: ReminderObservation?) =
        ReminderEntry(rule, TriggerRecord(rule.mode, rule.threshold, dataAt = observation?.dataAt))

    private fun data(vararg entries: ReminderEntry, checks: Map<ReminderSubject, ReminderCheckTimes> = emptyMap(),
        enabled: Boolean = true) = ReminderData(enabled, entries.toList(), checks)

    private fun plan(data: ReminderData, roles: Map<ReminderSubject, ReminderRoleInput>,
        delivery: ReminderDelivery = ReminderDelivery(), at: Long = now) = ReminderPlanner.plan(data, roles, delivery, at)

    @Test fun aRoleIsRequestedAtTheEarliestOfItsExpectedTimesAndItsCalibration() {
        val cached = sanity(120, now + 90 * minute)
        val checks = mapOf(arknights to ReminderCheckTimes(now - hour, now - hour))
        // Full in 90 minutes, calibrated an hour ago: the expected time comes first, and the request at that time
        // judges it, so there is nothing to work out before.
        val byTime = plan(data(judged(sanityFull, cached), checks = checks), mapOf(arknights to input { cached }))
        assertEquals(listOf(ReminderRoleTimes(arknights, now + 90 * minute)), byTime.roles)
        // Full in five hours: the calibration period (three hours from the last data) comes first.
        val later = sanity(85, now + 5 * hour)
        val byCalibration = plan(data(judged(sanityFull, later), checks = checks), mapOf(arknights to input { later }))
        assertEquals(now - hour + ReminderSchedule.CALIBRATION_PERIOD_MILLIS, byCalibration.roles.single().requestAt)
        // Without data for its rules a role waits for the calibration; a role never calibrated is due now.
        val unknown = plan(data(judged(sanityFull, null), checks = checks), mapOf(arknights to input()))
        assertEquals(now + 2 * hour, unknown.roles.single().requestAt)
        val never = plan(data(judged(sanityFull, null)), mapOf(arknights to input()))
        assertEquals(listOf(arknights), never.requests(now))
        assertEquals(now, never.wakeAt)
    }

    @Test fun twoRequestsOfARoleAreAtLeastThirtyMinutesApartAndKnownTimesFillTheGap() {
        // Calibrated by an exposure a minute ago, requested ten minutes ago, full in five minutes.
        val cached = sanity(134, now + 5 * minute, fetchedAt = now - minute)
        val checks = mapOf(arknights to ReminderCheckTimes(now - minute, now - 10 * minute))
        val plan = plan(data(judged(sanityFull, cached), checks = checks), mapOf(arknights to input { cached }))
        val times = plan.roles.single()
        assertEquals(now + 20 * minute, times.requestAt)
        // Sanity full may be worked out from its full time, so it is judged from the cache on time, without a request.
        assertEquals(now + 5 * minute, times.judgeAt)
        assertEquals(now + 5 * minute, plan.wakeAt)
        assertEquals(emptyList<ReminderSubject>(), plan.requests(now + 5 * minute))
        assertEquals(listOf(arknights), plan(data(judged(sanityFull, cached), checks = checks),
            mapOf(arknights to input { cached }), at = now + 5 * minute).judged(now + 5 * minute))
        // A rule that needs fresh data (Endfield sanity by percentage) only waits for the request.
        val endfieldCache = sanity(180, now + 4 * hour, now - minute, endfield, "endfield.sanity", 240)
        val endfieldChecks = mapOf(endfield to ReminderCheckTimes(now - minute, now - 10 * minute))
        val needsData = plan(data(judged(endfieldPercent, endfieldCache), checks = endfieldChecks),
            mapOf(endfield to input { endfieldCache })).roles.single()
        assertNull(needsData.judgeAt)
        assertTrue(needsData.requestAt >= now + 20 * minute)
    }

    @Test fun failuresBackOffFromTheLastDataUpToTheCalibrationPeriod() {
        // Endfield sanity by percentage needs fresh data, and the cache says it should be met already: the role is due
        // as soon as the interval and the backoff allow.
        val cached = sanity(180, now - 5 * minute, now - 50 * minute, endfield, "endfield.sanity", 240)
        val entry = judged(endfieldPercent, cached)
        fun requestAt(checks: ReminderCheckTimes, retryAt: Long = 0) = plan(data(entry, checks = mapOf(endfield to checks)),
            mapOf(endfield to input(retryAt) { cached })).roles.single().requestAt
        // Requested ten minutes ago, 40 minutes after the last data: the next request waits 40 minutes, not 30.
        assertEquals(now + 30 * minute, requestAt(ReminderCheckTimes(now - 50 * minute, now - 10 * minute)))
        // A failure soon after data still keeps the interval; a long outage waits the calibration period at most.
        assertEquals(now + 20 * minute, requestAt(ReminderCheckTimes(now - 15 * minute, now - 10 * minute)))
        assertEquals(now - 10 * minute + ReminderSchedule.CALIBRATION_PERIOD_MILLIS,
            requestAt(ReminderCheckTimes(now - 10 * hour, now - 10 * minute)))
        // Data since the request means it did not fail: only the interval applies.
        assertNull(ReminderPlanner.retryAfter(ReminderCheckTimes(now - 5 * minute, now - 10 * minute)))
        // A role never calibrated has no outage to measure: after a failure it waits the calibration period.
        assertEquals(now - 10 * minute + ReminderSchedule.CALIBRATION_PERIOD_MILLIS,
            requestAt(ReminderCheckTimes(lastRequestAt = now - 10 * minute)))
        // The account's rate limit is kept.
        assertEquals(now + 2 * hour, requestAt(ReminderCheckTimes(now - 4 * hour, now - 4 * hour), retryAt = now + 2 * hour))
    }

    @Test fun aRuleNotJudgedSinceItWasSavedWantsDataNow() {
        val cached = sanity(135, 0, fetchedAt = now - hour)
        val checks = mapOf(arknights to ReminderCheckTimes(now - hour, now - hour))
        // A rule just enabled (its record empty) on a role calibrated an hour ago: due now rather than in two hours.
        val fresh = plan(data(ReminderEntry(sanityFull), checks = checks), mapOf(arknights to input { cached }))
        assertEquals(listOf(arknights), fresh.requests(now))
        // Once judged (with data or without) it waits for the role's next check like any other rule.
        val kept = ReminderEntry(sanityFull, TriggerRecord(FULL))
        assertEquals(now + 2 * hour, plan(data(kept, checks = checks), mapOf(arknights to input { cached })).roles.single().requestAt)
        // Still within the interval of the last request.
        val recent = mapOf(arknights to ReminderCheckTimes(now - 10 * minute, now - 10 * minute))
        assertEquals(now + 20 * minute,
            plan(data(ReminderEntry(sanityFull), checks = recent), mapOf(arknights to input { cached })).roles.single().requestAt)
        // A rule of a metric this version does not know never waits, or its role would be requested every interval.
        val unknown = ReminderEntry(ReminderRule(arknights, "arknights.future_metric", FULL))
        assertEquals(now + 2 * hour, plan(data(unknown, checks = checks), mapOf(arknights to input())).roles.single().requestAt)
    }

    @Test fun nothingIsScheduledWhenRemindersCannotBeSent() {
        val entries = arrayOf(ReminderEntry(sanityFull), ReminderEntry(endfieldPercent))
        val roles = mapOf(arknights to input(), endfield to input())
        assertEquals(setOf(arknights, endfield), plan(data(*entries), roles).roles.map { it.subject }.toSet())
        // The master switch off, notifications off, or no enabled rule.
        assertNull(plan(data(*entries, enabled = false), roles).wakeAt)
        assertNull(plan(data(*entries), roles, ReminderDelivery(notificationsEnabled = false)).wakeAt)
        val disabled = arrayOf(ReminderEntry(sanityFull.copy(enabled = false)), ReminderEntry(endfieldPercent.copy(enabled = false)))
        assertNull(plan(data(*disabled), roles).wakeAt)
        // A game whose channel is off is skipped; the other game still is checked.
        val blocked = plan(data(*entries), roles, ReminderDelivery(blockedProviders = setOf(SklandGame.ENDFIELD.providerId)))
        assertEquals(listOf(arknights), blocked.roles.map { it.subject })
    }

    @Test fun rolesThatAreGoneAreNotScheduled() {
        val entries = arrayOf(ReminderEntry(sanityFull), ReminderEntry(endfieldPercent))
        // The Endfield role is no longer on the account: its rules are never planned, even before cleanup removes them.
        assertEquals(listOf(arknights), plan(data(*entries), mapOf(arknights to input())).roles.map { it.subject })
        // After cleanup the file holds no trace of it either.
        val store = ReminderStore(File(directory.root, "reminders/v1.json"))
        store.setEnabled(true)
        store.save(sanityFull)
        store.save(endfieldPercent)
        store.retainRoles("account-a", setOf(arknights))
        assertEquals(listOf(arknights), plan(store.read(), mapOf(arknights to input(), endfield to input())).roles.map { it.subject })
    }

    @Test fun eachRoleIsPlannedOnceWhateverItsRulesAndWidgets() {
        val drones = ReminderEntry(ReminderRule(arknights, "arknights.drones", PERCENT, 90))
        val plan = plan(data(ReminderEntry(sanityFull), drones, ReminderEntry(endfieldPercent)),
            mapOf(arknights to input(), endfield to input()))
        assertEquals(2, plan.roles.size)
        assertEquals(setOf(arknights, endfield), plan.requests(now).toSet())
    }
}
