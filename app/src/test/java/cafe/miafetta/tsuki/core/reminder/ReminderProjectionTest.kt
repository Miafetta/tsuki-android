package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.AWAITING_DATA
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOTIFIED
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOT_MET
import org.junit.Assert.*
import org.junit.Test

// Judging from known times when a check obtains no fresh data, and only for the modes the registry allows.
class ReminderProjectionTest : ReminderFixture() {
    @Test fun arknightsSanityPercentagesFollowThe360SecondRateWithoutFreshData() {
        val sanity = "arknights.sanity"
        val fullAt = now + 35 * 360 * second
        val cached = observe(sanity, 100, 135, fresh = false, fullAt = fullAt)
        // 80% of 135 is 108, reached 27 points (27 * 360 s) before full.
        val reach = fullAt - 27 * 360 * second
        val percent = rule(sanity, PERCENT, 80)
        val decisions = percent.walk(cached to reach - 1, cached to reach, cached to reach + hour,
            record = baseline(percent, cached))
        assertStatuses(listOf(NOT_MET, NOTIFIED, MET), decisions)
        val notice = decisions[1].notice!!
        assertEquals("理智已达到 80%", notice.body)
        assertTrue(notice.estimated)
        assertEquals(reach, decisions[1].record.firedAt)
        val value = rule(sanity, VALUE, 120)
        assertStatuses(listOf(NOT_MET, NOTIFIED), value.walk(cached to fullAt - 15 * 360 * second - 1,
            cached to fullAt - 15 * 360 * second, record = baseline(value, cached)))
        val full = rule(sanity, FULL)
        assertStatuses(listOf(NOT_MET, NOTIFIED), full.walk(cached to fullAt - 1, cached to fullAt, record = baseline(full, cached)))
    }

    @Test fun cachedDataNeverReportsBeforeTheRuleHasJudgedFreshData() {
        // A rule just enabled (or changed) sees only a cache from before: reporting from it would replay history, such
        // as a round completed and collected meanwhile, so it waits for fresh data.
        val sanity = "arknights.sanity"
        val cachedFull = observe(sanity, 135, 135, fresh = false, fullAt = 0)
        assertEquals(AWAITING_DATA, judge(rule(sanity, FULL), cachedFull).status)
        val due = observe(sanity, 100, 135, fresh = false, fullAt = now)
        assertEquals(AWAITING_DATA, judge(rule(sanity, PERCENT, 80), due, at = now + hour).status)
        val event = rule("arknights.training", ReminderMode.EVENT)
        val completed = observe("arknights.training", 0, null, fresh = false, round = ROUND_A, event = EventState.COMPLETE)
        assertEquals(AWAITING_DATA, judge(event, completed).status)
        assertFalse(judge(event, completed).notify)
        // Once fresh data was judged, a cache older than it is ignored rather than worked out.
        val judged = TriggerRecord(ReminderMode.FULL, dataAt = now)
        val older = observe(sanity, 135, 135, fresh = false, fullAt = 0, dataAt = now - hour)
        assertEquals(ReminderDecision(ReminderStatus.STALE, judged), judge(rule(sanity, FULL), older, judged, now + hour))
    }

    @Test fun dronesFollowTheLinearRecoveryWithoutFreshData() {
        val drones = "arknights.drones"
        val cached = observe(drones, 100, 200, fresh = false, fullAt = now + 10_000 * second)
        // Half way from 100 to 200 is half way in time.
        val reach = now + 5_000 * second
        for (rule in listOf(rule(drones, VALUE, 150), rule(drones, PERCENT, 75))) {
            assertStatuses(listOf(NOT_MET, NOTIFIED), rule.walk(cached to reach - 1, cached to reach, record = baseline(rule, cached)))
        }
        val full = rule(drones, FULL)
        assertStatuses(listOf(NOT_MET, NOTIFIED), full.walk(cached to now + 10_000 * second - 1,
            cached to now + 10_000 * second, record = baseline(full, cached)))
    }

    @Test fun endfieldSanityIsFullByMaxTsButPercentagesNeedFreshData() {
        val sanity = "endfield.sanity"
        val fullAt = now + 40 * 432 * second
        val cached = observe(sanity, 200, 240, fresh = false, fullAt = fullAt)
        val percent = rule(sanity, PERCENT, 90)
        // The 432 s estimate only plans the check: 90% of 240 (216) is expected 24 points before full.
        val waiting = percent.walk(cached to fullAt - hour, cached to fullAt + hour)
        assertStatuses(listOf(AWAITING_DATA, AWAITING_DATA), waiting)
        assertEquals(listOf(fullAt - 24 * 432 * second, fullAt - 24 * 432 * second), waiting.map { it.expectedAt })
        assertEquals(TriggerRecord(PERCENT, 90), waiting.last().record)
        assertStatuses(listOf(AWAITING_DATA), rule(sanity, VALUE, 230).walk(cached to fullAt))
        val fresh = observe(sanity, 216, 240, fetchedAt = fullAt - hour, fullAt = fullAt)
        assertStatuses(listOf(NOTIFIED), percent.walk(fresh to fullAt - hour))
        assertFalse(percent.walk(fresh to fullAt - hour).single().notice!!.estimated)
        val full = rule(sanity, FULL)
        assertStatuses(listOf(NOT_MET, NOTIFIED), full.walk(cached to fullAt - 1, cached to fullAt, record = baseline(full, cached)))
    }

    @Test fun tradingAndManufactureNeedFreshData() {
        for (metric in listOf("arknights.trading", "arknights.manufacture")) {
            val cached = observe(metric, 9, 20, fresh = false, nextAt = now + hour, fullAt = now + hour)
            for (rule in listOf(rule(metric, FULL), rule(metric, PERCENT, 45), rule(metric, VALUE, 10))) {
                val decision = judge(rule, cached, at = now + 10 * hour)
                assertEquals("$rule", AWAITING_DATA, decision.status)
                assertFalse(decision.notify)
            }
            val fresh = observe(metric, 10, 20, fetchedAt = now + 2 * hour)
            assertEquals(NOTIFIED, judge(rule(metric, VALUE, 10), fresh, at = now + 2 * hour).status)
        }
    }

    @Test fun metricsWithoutTimesAreNotWorkedOut() {
        // A completion time would not be used for the dormitory before the registry plans by it.
        val rest = "arknights.dormitory_rest"
        val cached = observe(rest, 10, 20, fresh = false, fullAt = now)
        assertEquals(AWAITING_DATA, judge(rule(rest, FULL), cached, at = now + hour).status)
        assertNull(expected(rule(rest, FULL), cached.copy(fullAt = now + hour)))
        val activation = "endfield.daily_activation"
        assertEquals(AWAITING_DATA, judge(rule(activation, FULL), observe(activation, 100, 100, fresh = false)).status)
        // Recruitment is full once its last running slot finishes.
        val recruit = observe("arknights.recruit", 2, 4, fresh = false, fullAt = now + hour)
        val allDone = rule("arknights.recruit", FULL)
        assertStatuses(listOf(NOT_MET, NOTIFIED), allDone.walk(recruit to now + hour - 1, recruit to now + hour,
            record = baseline(allDone, recruit)))
    }

    @Test fun freshDataReplacesThePlan() {
        val sanity = "arknights.sanity"
        val value = rule(sanity, VALUE, 120)
        val first = observe(sanity, 125, 135, dataAt = 1, fullAt = now + 10 * 360 * second)
        // Sanity spent: fresh data re-arms the rule and the expected time follows the new full time.
        val spentAt = now + hour
        val spentFull = spentAt + 115 * 360 * second
        val spent = observe(sanity, 20, 135, fetchedAt = spentAt, dataAt = 2, fullAt = spentFull)
        val decisions = value.walk(first to now, spent to spentAt)
        assertStatuses(listOf(NOTIFIED, NOT_MET), decisions)
        assertNull(decisions[0].expectedAt)
        assertEquals(spentFull - 15 * 360 * second, decisions[1].expectedAt)
        assertFalse(decisions[1].record.fired)
        // The plan is met by known times and reports again.
        val again = value.walk(spent.copy(fresh = false) to spentFull - 15 * 360 * second, record = decisions[1].record)
        assertStatuses(listOf(NOTIFIED), again)
    }
}
