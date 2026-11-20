package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.stableId
import cafe.miafetta.tsuki.providers.EndfieldParser
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// The capability registry: which metrics offer which modes, how rules are validated, and the notification bodies.
class ReminderCapabilitiesTest : ReminderFixture() {
    private val numeric = setOf(FULL, PERCENT, VALUE)

    @Test fun theRegistryHoldsTheFirstReleaseScope() {
        val modes = mapOf(
            "arknights.sanity" to numeric, "arknights.drones" to numeric, "arknights.dormitory_rest" to numeric,
            "arknights.training" to setOf(EVENT), "arknights.recruit" to setOf(FULL), "arknights.trading" to numeric,
            "arknights.manufacture" to numeric, "endfield.sanity" to numeric, "endfield.daily_activation" to numeric,
            "endfield.weekly_mission" to numeric, "endfield.pass_level" to numeric,
        )
        assertEquals(modes.keys, ReminderCapabilities.metricIds)
        assertEquals(modes, modes.mapValues { (id, _) -> ReminderCapabilities[id]!!.modes })
        val projected = ReminderCapabilities.metricIds.associateWith { ReminderCapabilities[it]!!.projected }
            .filterValues { it.isNotEmpty() }
        assertEquals(mapOf("arknights.sanity" to numeric, "arknights.drones" to numeric, "arknights.training" to setOf(EVENT),
            "arknights.recruit" to setOf(FULL), "endfield.sanity" to setOf(FULL)), projected)
        val timing = ReminderCapabilities.metricIds.associateWith { ReminderCapabilities[it]!!.timing }
        assertEquals(setOf("arknights.trading", "arknights.manufacture"), timing.filterValues { it == ReminderTiming.NEXT_OUTPUT }.keys)
        assertEquals(setOf("arknights.dormitory_rest", "endfield.daily_activation", "endfield.weekly_mission",
            "endfield.pass_level"), timing.filterValues { it == ReminderTiming.NONE }.keys)
        assertEquals(RecoveryModel.FixedRate(360_000), ReminderCapabilities["arknights.sanity"]!!.recovery)
        assertEquals(RecoveryModel.Linear, ReminderCapabilities["arknights.drones"]!!.recovery)
        assertEquals(RecoveryModel.FixedRate(432_000), ReminderCapabilities["endfield.sanity"]!!.recovery)
        for (id in listOf("endfield.seek_suspicion", "arknights.recruit_refresh", "arknights.clues", "arknights.tired",
            "arknights.daily", "arknights.annihilation", "arknights.tower")) {
            assertNull(id, ReminderCapabilities[id])
        }
    }

    @Test fun metricsOutsideTheRegistryAndModesTheyDoNotOfferAreRejected() {
        assertEquals(RuleCheck.UNKNOWN_METRIC, ReminderCapabilities.check(rule("endfield.seek_suspicion", FULL)))
        assertEquals(RuleCheck.UNKNOWN_METRIC, ReminderCapabilities.check(rule("arknights.clues", VALUE, 3)))
        assertEquals(RuleCheck.UNSUPPORTED_MODE, ReminderCapabilities.check(rule("arknights.recruit", PERCENT, 50)))
        assertEquals(RuleCheck.UNSUPPORTED_MODE, ReminderCapabilities.check(rule("arknights.training", VALUE, 1)))
        assertEquals(RuleCheck.UNSUPPORTED_MODE, ReminderCapabilities.check(rule("arknights.sanity", EVENT)))
        assertEquals(RuleCheck.VALID, ReminderCapabilities.check(rule("arknights.recruit", FULL)))
        // The judge refuses them as well and keeps whatever record it was given.
        val record = TriggerRecord(PERCENT, 50, fired = true, firedAt = 1)
        val decision = judge(rule("arknights.recruit", PERCENT, 50), observe("arknights.recruit", 4, 4), record)
        assertEquals(ReminderDecision(ReminderStatus.UNSUPPORTED, record), decision)
        val unknown = rule("arknights.clues", VALUE, 3)
        assertEquals(ReminderStatus.UNSUPPORTED, judge(unknown, observe("arknights.clues", 7, 7)).status)
    }

    @Test fun modesThatNeedALimitAreRejectedWhileItIsUnknown() {
        // Manufacture with products but no formula has no known capacity: only VALUE is on offer.
        val manufacture = ReminderCapabilities["arknights.manufacture"]!!
        assertEquals(setOf(VALUE), manufacture.modesFor(null))
        assertEquals(numeric, manufacture.modesFor(64))
        assertEquals(RuleCheck.NEEDS_LIMIT, ReminderCapabilities.checkAgainst(rule("arknights.manufacture", PERCENT, 50), null))
        assertEquals(RuleCheck.NEEDS_LIMIT, ReminderCapabilities.checkAgainst(rule("arknights.manufacture", FULL), null))
        assertEquals(RuleCheck.VALID, ReminderCapabilities.checkAgainst(rule("arknights.manufacture", VALUE, 30), null))
        assertEquals(RuleCheck.VALID, ReminderCapabilities.checkAgainst(rule("arknights.manufacture", PERCENT, 50), 64))
        assertEquals(RuleCheck.NEEDS_LIMIT, ReminderCapabilities.checkAgainst(rule("arknights.trading", FULL), 0))
        // Value thresholds are bounded by the current limit, percentages by 100.
        assertEquals(RuleCheck.ABOVE_LIMIT, ReminderCapabilities.checkAgainst(rule("arknights.sanity", VALUE, 136), 135))
        assertEquals(RuleCheck.VALID, ReminderCapabilities.checkAgainst(rule("arknights.sanity", VALUE, 135), 135))
        assertEquals(RuleCheck.UNSUPPORTED_MODE, ReminderCapabilities.checkAgainst(rule("arknights.training", VALUE, 1), 1))
        assertEquals(1..100, manufacture.thresholdRange(PERCENT, null))
        assertEquals(1..64, manufacture.thresholdRange(VALUE, 64))
        assertEquals(1..Int.MAX_VALUE, manufacture.thresholdRange(VALUE, null))
        assertNull(manufacture.thresholdRange(FULL, 64))
    }

    @Test fun ruleShapesAreCheckedWhenRulesAreMade() {
        for ((mode, threshold) in listOf(PERCENT to 0, PERCENT to 101, PERCENT to null, VALUE to 0, VALUE to null,
            FULL to 1, EVENT to 1)) {
            assertThrows("$mode $threshold", IllegalArgumentException::class.java) { rule("arknights.sanity", mode, threshold) }
        }
        assertThrows(IllegalArgumentException::class.java) { rule(" ", FULL) }
        assertThrows(IllegalArgumentException::class.java) { ReminderSubject("skland", "", "000000001") }
        assertEquals(100, rule("arknights.sanity", PERCENT, 100).threshold)
        assertEquals(Int.MAX_VALUE, rule("arknights.sanity", VALUE, Int.MAX_VALUE).threshold)
    }

    @Test fun everyModeOfEveryMetricHasABody() {
        val bodies = ReminderCapabilities.metricIds.flatMap { id ->
            val capability = ReminderCapabilities[id]!!
            ReminderMode.entries.filter { it in capability.modes }.map { mode ->
                val threshold = when (mode) {
                    PERCENT -> 80
                    VALUE -> 120
                    else -> null
                }
                "$id $mode: ${capability.texts.body(mode, threshold)}"
            }
        }
        assertEquals(BODIES, bodies)
        for (body in bodies) {
            assertFalse(body, '·' in body || '{' in body || '}' in body || "null" in body)
        }
    }

    @Test fun registeredIdsAreTheIdsTheDataSourcesWrite() {
        val fixture = FakeArknightsAdapter()
        val config = WidgetInstanceConfig(10, fixture.id, "skland.arknights.status", "demo-1", "amiya")
        val arknightsIds = fixture.fetch(config, now).metrics.mapNotNull { it.stableId(arknightsSource = true) }.toSet()
        val card = JSONObject(requireNotNull(javaClass.getResource("/skland/endfield-card-redacted.json")).readText())
        val endfieldIds = EndfieldParser.snapshot(card, "0000000011", now).metrics.mapNotNull { it.metricId }.toSet()
        assertEquals(ReminderCapabilities.metricIds.filter { it.startsWith("arknights.") }.toSet(),
            ReminderCapabilities.metricIds.filter { it in arknightsIds }.toSet())
        assertEquals(ReminderCapabilities.metricIds.filter { it.startsWith("endfield.") }.toSet(),
            ReminderCapabilities.metricIds.filter { it in endfieldIds }.toSet())
        assertEquals(setOf("endfield.seek_suspicion"), endfieldIds - ReminderCapabilities.metricIds)
        assertTrue(EndfieldParser.SANITY in ReminderCapabilities.metricIds)
    }

    private companion object {
        val BODIES = listOf(
            "arknights.sanity FULL: 理智已满",
            "arknights.sanity PERCENT: 理智已达到 80%",
            "arknights.sanity VALUE: 理智已达到 120",
            "arknights.drones FULL: 无人机已满",
            "arknights.drones PERCENT: 无人机已达到 80%",
            "arknights.drones VALUE: 无人机已达到 120",
            "arknights.dormitory_rest FULL: 宿舍休息已全部完成",
            "arknights.dormitory_rest PERCENT: 休息完成比例已达到 80%",
            "arknights.dormitory_rest VALUE: 已有 120 名干员休息完成",
            "arknights.training EVENT: 训练室专精训练已完成",
            "arknights.recruit FULL: 公开招募已全部完成",
            "arknights.trading FULL: 订单已满",
            "arknights.trading PERCENT: 订单进度已达到 80%",
            "arknights.trading VALUE: 订单进度已达到 120",
            "arknights.manufacture FULL: 制造站产物已满",
            "arknights.manufacture PERCENT: 制造进度已达到 80%",
            "arknights.manufacture VALUE: 制造进度已达到 120",
            "endfield.sanity FULL: 理智已满",
            "endfield.sanity PERCENT: 理智已达到 80%",
            "endfield.sanity VALUE: 理智已达到 120",
            "endfield.daily_activation FULL: 活跃度已满",
            "endfield.daily_activation PERCENT: 活跃度已达到 80%",
            "endfield.daily_activation VALUE: 活跃度已达到 120",
            "endfield.weekly_mission FULL: 每周事务已完成",
            "endfield.weekly_mission PERCENT: 每周事务已达到 80%",
            "endfield.weekly_mission VALUE: 每周事务已达到 120",
            "endfield.pass_level FULL: 通行证已满级",
            "endfield.pass_level PERCENT: 通行证已达到 80%",
            "endfield.pass_level VALUE: 通行证已达到 120 级",
        )
    }
}
