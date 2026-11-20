package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NEEDS_REVIEW
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOTIFIED
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOT_MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.UNAVAILABLE
import org.junit.Assert.*
import org.junit.Test

// Threshold boundaries: "reached or passed", values above the limit, limit changes and missing denominators.
class ReminderThresholdTest : ReminderFixture() {
    private val sanity = "arknights.sanity"

    @Test fun reachingAThresholdExactlyOrCrossingItMeetsTheRule() {
        val value = rule(sanity, VALUE, 120)
        assertStatuses(listOf(NOT_MET, NOTIFIED), value.walk(observe(sanity, 119, 135) to now,
            observe(sanity, 120, 135) to now + minute))
        assertStatuses(listOf(NOT_MET, NOTIFIED), value.walk(observe(sanity, 118, 135) to now,
            observe(sanity, 125, 135) to now + minute))
        // 80% of 135 is 108: 107 is short of it, 108 reaches it.
        val percent = rule(sanity, PERCENT, 80)
        assertStatuses(listOf(NOT_MET, NOTIFIED), percent.walk(observe(sanity, 107, 135) to now,
            observe(sanity, 108, 135) to now + minute))
        assertStatuses(listOf(NOT_MET, NOTIFIED), rule(sanity, FULL).walk(observe(sanity, 134, 135) to now,
            observe(sanity, 135, 135) to now + minute))
    }

    @Test fun percentagesAreComparedWithoutFloatingPointErrors() {
        // 29 / 100 * 100 is 28.999999999999996 in floating point, which would miss a 29% rule.
        assertEquals(NOTIFIED, judge(rule("endfield.weekly_mission", PERCENT, 29), observe("endfield.weekly_mission", 29, 100)).status)
        assertEquals(NOTIFIED, judge(rule("endfield.weekly_mission", PERCENT, 33), observe("endfield.weekly_mission", 1, 3)).status)
        assertEquals(NOT_MET, judge(rule("endfield.weekly_mission", PERCENT, 34), observe("endfield.weekly_mission", 1, 3)).status)
        // Large values do not overflow.
        assertEquals(NOTIFIED, judge(rule("endfield.weekly_mission", PERCENT, 100),
            observe("endfield.weekly_mission", 10_000_000, 10_000_000)).status)
    }

    @Test fun arknightsSanityAboveItsLimitCountsAsFull() {
        // Items can raise sanity above the natural limit.
        val observation = observe(sanity, 150, 135, fullAt = 0)
        for (rule in listOf(rule(sanity, FULL), rule(sanity, PERCENT, 100), rule(sanity, VALUE, 135))) {
            assertEquals(rule.toString(), NOTIFIED, judge(rule, observation).status)
        }
    }

    @Test fun limitChangesAreJudgedAgain() {
        val rest = "arknights.dormitory_rest"
        val percent = rule(rest, PERCENT, 80)
        // More resting operators lower the share below 80%, which re-arms the rule; fewer raise it again.
        val decisions = percent.walk(observe(rest, 16, 20) to now, observe(rest, 16, 24) to now + hour,
            observe(rest, 16, 20) to now + 2 * hour, observe(rest, 18, 24) to now + 3 * hour,
            observe(rest, 18, 22) to now + 4 * hour)
        assertStatuses(listOf(NOTIFIED, NOT_MET, NOTIFIED, NOT_MET, NOTIFIED), decisions)
        assertFalse(decisions[1].record.fired)
        // A smaller limit can turn a FULL rule met without the value changing.
        assertStatuses(listOf(NOT_MET, NOTIFIED), rule(rest, FULL).walk(observe(rest, 18, 20) to now,
            observe(rest, 18, 18) to now + hour))
    }

    @Test fun observationsWithoutAUsableDenominatorTakeNoPart() {
        val fired = TriggerRecord(FULL, null, fired = true, firedAt = now - hour, dataAt = now - hour)
        val unusable = listOf(observe(sanity, 10, null), observe(sanity, 0, 0), observe(sanity, 0, 0, fullAt = 0),
            observe(sanity, 135, 135, state = ObservationState.UNKNOWN),
            observe(sanity, 135, 135, state = ObservationState.LOCKED),
            observe(sanity, 135, 135, state = ObservationState.NO_OBJECT))
        for (observation in unusable) {
            val decision = judge(rule(sanity, FULL), observation, fired)
            assertEquals(observation.toString(), ReminderDecision(UNAVAILABLE, fired), decision)
            // Never full either for a rule that has not reported yet.
            val fresh = judge(rule(sanity, FULL), observation)
            assertEquals(ReminderDecision(UNAVAILABLE, TriggerRecord(FULL, null)), fresh)
        }
        val percent = TriggerRecord(PERCENT, 50)
        assertEquals(ReminderDecision(UNAVAILABLE, percent), judge(rule(sanity, PERCENT, 50), observe(sanity, 10, null), percent))
        assertEquals(UNAVAILABLE, judge(rule(sanity, VALUE, 5), observe(sanity, 10, 0)).status)
    }

    @Test fun valueRulesWorkWithoutALimit() {
        // Manufacture with an unknown capacity still has a product count.
        val manufacture = "arknights.manufacture"
        assertStatuses(listOf(NOT_MET, NOTIFIED), rule(manufacture, VALUE, 30).walk(observe(manufacture, 29, null) to now,
            observe(manufacture, 31, null) to now + hour))
    }

    @Test fun aRuleAlreadyMetWhenEnabledReportsOnceOnItsFirstValidData() {
        val full = rule(sanity, FULL)
        val decisions = full.walk(observe(sanity, 0, null) to now, observe(sanity, 135, 135) to now + minute,
            observe(sanity, 135, 135) to now + 2 * minute)
        assertStatuses(listOf(UNAVAILABLE, NOTIFIED, MET), decisions)
        val notice = decisions[1].notice!!
        assertEquals(ReminderNotice(arknights, sanity, FULL, null, "理智已满", estimated = false), notice)
        assertEquals(TriggerRecord(FULL, null, fired = true, firedAt = now + minute, dataAt = now), decisions[1].record)
    }

    @Test fun valueThresholdsAboveTheLimitNeedReviewAndAreNotRewritten() {
        val value = rule(sanity, VALUE, 140)
        val decisions = value.walk(observe(sanity, 135, 135) to now, observe(sanity, 150, 135) to now + minute,
            observe(sanity, 140, 140) to now + hour)
        assertStatuses(listOf(NEEDS_REVIEW, NEEDS_REVIEW, NOTIFIED), decisions)
        assertEquals(TriggerRecord(VALUE, 140), decisions[1].record)
        assertNull(decisions[0].expectedAt)
        assertEquals(140, value.threshold)
    }

    @Test fun anExplicitFullTimeCountsAsFull() {
        // A full time at or before the data time says the metric was full even when the value lags behind.
        val endfieldSanity = "endfield.sanity"
        assertEquals(NOTIFIED, judge(rule(endfieldSanity, FULL), observe(endfieldSanity, 239, 240, fullAt = 0)).status)
        assertEquals(NOTIFIED, judge(rule(endfieldSanity, FULL), observe(endfieldSanity, 239, 240, fullAt = now)).status)
        assertEquals(NOT_MET, judge(rule(endfieldSanity, FULL), observe(endfieldSanity, 239, 240, fullAt = now + minute)).status)
    }
}
