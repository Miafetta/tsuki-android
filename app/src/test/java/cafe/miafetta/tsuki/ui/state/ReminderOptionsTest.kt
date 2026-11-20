package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.*
import org.junit.Test

// The metric rows of the reminder account pages: which metrics each game lists and in what order, which are switches,
// the conditions a metric offers with and without a limit, its condition menu, the rule a choice leaves (none for
// "关闭"), the thresholds a mode starts with, how a threshold reads, which thresholds can be changed, and that no text
// on the pages uses "·".
class ReminderOptionsTest {
    private fun capability(metricId: String) = ReminderCapabilities[metricId]!!
    private val subject = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")

    @Test fun rowsInPageOrder() {
        val arknights = ReminderOptions.rows(SklandGame.ARKNIGHTS)
        assertEquals(listOf("arknights.sanity", "arknights.drones", "arknights.dormitory_rest", "arknights.trading",
            "arknights.manufacture", "arknights.training", "arknights.recruit"), arknights.map { it.metricId })
        assertEquals(listOf("理智", "无人机", "休息进度", "订单进度", "制造进度", "训练室专精训练完成", "公开招募全部完成"),
            arknights.map(ReminderOptions::title))
        val endfield = ReminderOptions.rows(SklandGame.ENDFIELD)
        assertEquals(listOf("endfield.sanity", "endfield.daily_activation", "endfield.weekly_mission", "endfield.pass_level"),
            endfield.map { it.metricId })
        assertEquals(listOf("理智", "活跃度", "每周事务", "通行证"), endfield.map(ReminderOptions::title))
    }

    // The page shows the listed rows only, so every registered metric of a game has to be one of its rows, exactly once,
    // and a game lists nothing of another game.
    @Test fun rowsHoldEveryRegisteredMetricOnce() {
        for (game in SklandGame.entries) {
            val rows = ReminderOptions.rows(game).map { it.metricId }
            assertEquals(game.name, rows.size, rows.toSet().size)
            assertEquals(game.name, ReminderOptions.metrics(game).map { it.metricId }.toSet(), rows.toSet())
        }
        val all = SklandGame.entries.flatMap { ReminderOptions.rows(it) }.map { it.metricId }
        assertEquals(ReminderCapabilities.metricIds, all.toSet())
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun eachGameListsItsMetricsInRegistryOrder() {
        assertEquals(listOf("arknights.sanity", "arknights.drones", "arknights.dormitory_rest", "arknights.training",
            "arknights.recruit", "arknights.trading", "arknights.manufacture"),
            ReminderOptions.metrics(SklandGame.ARKNIGHTS).map { it.metricId })
        assertEquals(listOf("endfield.sanity", "endfield.daily_activation", "endfield.weekly_mission", "endfield.pass_level"),
            ReminderOptions.metrics(SklandGame.ENDFIELD).map { it.metricId })
        // Nothing outside the registry, and every registered metric belongs to exactly one game.
        val listed = SklandGame.entries.flatMap { ReminderOptions.metrics(it) }.map { it.metricId }
        assertEquals(ReminderCapabilities.metricIds, listed.toSet())
        assertEquals(listed.size, listed.toSet().size)
    }

    @Test fun onlyTheTwoSingleModeMetricsAreSwitches() {
        val switches = SklandGame.entries.flatMap { ReminderOptions.rows(it) }.filter(ReminderOptions::isSwitch)
        assertEquals(listOf("arknights.training", "arknights.recruit"), switches.map { it.metricId })
        assertEquals(listOf("训练室专精训练完成", "公开招募全部完成"), switches.map(ReminderOptions::title))
        assertEquals("理智", ReminderOptions.title(capability("endfield.sanity")))
    }

    @Test fun conditionsFollowTheLimit() {
        val sanity = capability("arknights.sanity")
        assertEquals(listOf(FULL, PERCENT, VALUE), ReminderOptions.conditions(sanity, 135))
        assertEquals(listOf(VALUE), ReminderOptions.conditions(sanity, null))
        // "0/0" is no limit to be full of either.
        assertEquals(listOf(VALUE), ReminderOptions.conditions(sanity, 0))
        assertEquals(listOf("已满时提醒", "达到百分比时提醒", "达到数值时提醒"),
            ReminderOptions.conditions(sanity, 135).map(ReminderOptions::conditionLabel))
        // A saved percentage rule keeps its entry while the limit is unknown, so the page still shows it.
        assertEquals(listOf(PERCENT, VALUE), ReminderOptions.conditions(sanity, null, PERCENT))
        assertEquals(listOf(VALUE), ReminderOptions.conditions(sanity, null, VALUE))
        // An event metric is a switch alone and lists no condition.
        assertEquals(emptyList<ReminderMode>(), ReminderOptions.conditions(capability("arknights.training"), null))
    }

    // The menu starts with "关闭" and lists the conditions the limit allows, and an enabled rule's condition the limit no
    // longer allows; nothing is selected but "关闭" while the rule is off.
    @Test fun theConditionMenu() {
        val sanity = capability("arknights.sanity")
        fun labels(limit: Int?, stored: ReminderMode?) = ReminderOptions.choices(sanity, limit, stored).map(ReminderOptions::choiceLabel)
        assertEquals(listOf(null, FULL, PERCENT, VALUE), ReminderOptions.choices(sanity, 135, null))
        assertEquals(listOf("关闭", "已满时提醒", "达到百分比时提醒", "达到数值时提醒"), labels(135, null))
        assertEquals(listOf("关闭", "达到数值时提醒"), labels(null, null))
        assertEquals(listOf("关闭", "达到百分比时提醒", "达到数值时提醒"), labels(null, PERCENT))
        assertEquals(listOf("关闭", "达到数值时提醒"), labels(0, VALUE))
        assertEquals(0, ReminderOptions.choices(sanity, 135, null).indexOf(null))
        assertEquals(2, ReminderOptions.choices(sanity, 135, PERCENT).indexOf(PERCENT))
        assertEquals(ReminderOptions.OFF, ReminderOptions.choiceLabel(null))
    }

    // "关闭" leaves no rule, so the stored one is deleted; a condition leaves the rule with it and its default threshold
    // (full takes none), or the threshold it had before in this session while that still fits the range. A switch saves
    // its single mode the same way.
    @Test fun theRuleAChoiceLeaves() {
        val sanity = capability("arknights.sanity")
        fun rule(mode: ReminderMode, threshold: Int? = null) = ReminderRule(subject, "arknights.sanity", mode, threshold)
        assertNull(ReminderOptions.chosen(subject, sanity, null, 60, 135))
        assertEquals(rule(FULL), ReminderOptions.chosen(subject, sanity, FULL, null, 135))
        assertEquals(rule(PERCENT, 80), ReminderOptions.chosen(subject, sanity, PERCENT, null, 135))
        assertEquals(rule(VALUE, 135), ReminderOptions.chosen(subject, sanity, VALUE, null, 135))
        assertEquals(rule(VALUE, 1), ReminderOptions.chosen(subject, sanity, VALUE, null, null))
        assertEquals(rule(VALUE, 1), ReminderOptions.chosen(subject, sanity, VALUE, null, 0))
        // Back to a condition used before: its threshold comes back while it fits.
        assertEquals(rule(PERCENT, 60), ReminderOptions.chosen(subject, sanity, PERCENT, 60, 135))
        assertEquals(rule(VALUE, 120), ReminderOptions.chosen(subject, sanity, VALUE, 120, 135))
        // A value above a limit that has dropped meanwhile starts from the default instead.
        assertEquals(rule(VALUE, 100), ReminderOptions.chosen(subject, sanity, VALUE, 120, 100))
        assertEquals(rule(FULL), ReminderOptions.chosen(subject, sanity, FULL, 60, 135))
        val training = capability("arknights.training")
        assertEquals(ReminderRule(subject, "arknights.training", EVENT),
            ReminderOptions.chosen(subject, training, training.modes.single(), null, null))
        val recruit = capability("arknights.recruit")
        assertEquals(ReminderRule(subject, "arknights.recruit", FULL),
            ReminderOptions.chosen(subject, recruit, recruit.modes.single(), null, null))
    }

    @Test fun defaultThresholds() {
        assertEquals(80, ReminderOptions.defaultThreshold(PERCENT, null))
        assertEquals(80, ReminderOptions.defaultThreshold(PERCENT, 135))
        assertEquals(135, ReminderOptions.defaultThreshold(VALUE, 135))
        assertEquals(1, ReminderOptions.defaultThreshold(VALUE, null))
        assertEquals(1, ReminderOptions.defaultThreshold(VALUE, 0))
        assertNull(ReminderOptions.defaultThreshold(FULL, 135))
        assertNull(ReminderOptions.defaultThreshold(EVENT, null))
    }

    // A threshold as the page writes it: a percentage, or the value alone, also above a limit that has since dropped.
    @Test fun thresholdTexts() {
        assertEquals("80%", ReminderOptions.thresholdText(PERCENT, 80))
        assertEquals("160", ReminderOptions.thresholdText(VALUE, 160))
        assertEquals("1", ReminderOptions.thresholdText(VALUE, 1))
    }

    @Test fun limitsComeFromTheSnapshotAndUnreadMetricsAreUnknown() {
        val snapshot = WidgetSnapshot("角色", listOf(WidgetMetric("理智", 120, 135, null, metricId = "arknights.sanity"),
            WidgetMetric("无人机", 50, 170, null, metricId = "arknights.drones")), 1_793_956_830_000L)
        val metrics = ReminderOptions.metrics(SklandGame.ARKNIGHTS)
        val limits = ReminderOptions.limits(subject, snapshot, metrics)
        assertEquals(135, limits["arknights.sanity"])
        assertEquals(170, limits["arknights.drones"])
        // A metric the snapshot lacks has an unknown limit.
        assertNull(limits["arknights.trading"])
        assertTrue(ReminderOptions.limits(subject, null, metrics).isEmpty())
    }

    // A stored percentage the unknown limit no longer allows is listed in the menu and can only be kept: its threshold
    // row is disabled. A value can always be changed.
    @Test fun aConditionTheLimitNoLongerAllowsIsKeptAsItIs() {
        val sanity = capability("arknights.sanity")
        assertFalse(ReminderOptions.adjustable(sanity, PERCENT, null))
        assertTrue(ReminderOptions.adjustable(sanity, PERCENT, 135))
        assertTrue(ReminderOptions.adjustable(sanity, VALUE, null))
        assertTrue(ReminderOptions.adjustable(sanity, VALUE, 135))
    }

    // Every text the account pages take from ReminderOptions and ThresholdSlider; the role labels are checked in
    // ReminderRolesTest, the account rows in ReminderAccountsTest.
    @Test fun noTextUsesTheMiddleDot() {
        val metrics = SklandGame.entries.flatMap { ReminderOptions.rows(it) }
        val menus = metrics.flatMap { capability ->
            listOf(null, 135).flatMap { limit -> ReminderOptions.choices(capability, limit, null) }
        }.distinct().map(ReminderOptions::choiceLabel)
        val values = listOf(ReminderOptions.thresholdText(PERCENT, 80), ReminderOptions.thresholdText(VALUE, 160))
        val scales = metrics.flatMap { capability ->
            listOf(PERCENT to 135, VALUE to 210).mapNotNull { (mode, limit) -> ThresholdSlider.of(capability, mode, limit) }
        }.flatMap { listOf(it.valueLabel(80), it.endLabel) }
        val texts = metrics.map(ReminderOptions::title) + ReminderMode.entries.map(ReminderOptions::conditionLabel) +
            menus + values + scales + listOf(ReminderOptions.OFF, ReminderOptions.THRESHOLD, ReminderOptions.DECREASE,
                ReminderOptions.INCREASE)
        assertEquals(4, menus.size)
        assertTrue(scales.isNotEmpty())
        assertTrue(texts.none { "·" in it })
    }
}
