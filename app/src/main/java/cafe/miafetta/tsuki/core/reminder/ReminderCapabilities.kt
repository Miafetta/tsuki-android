package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE

// The metrics that offer reminders, looked up by metric ID; a metric without an entry offers no rules. The IDs are the
// stable metric identities the data sources write (core/MetricIds.kt for Arknights, EndfieldParser for Endfield);
// ReminderCapabilitiesTest checks that both still produce every ID listed here.
//
// Not offered: Endfield's seek_suspicion (its reset rule has no basis), and Arknights' recruit_refresh, clues, tired,
// daily, annihilation and tower.
object ReminderCapabilities {
    // Percentages are whole percents. A value threshold starts at one; its upper bound is the metric's observed
    // effective limit when it is judged (ReminderCapability.thresholdRange), never a fixed game capacity.
    val PERCENT_RANGE = 1..100
    const val MIN_VALUE = 1

    private val NUMERIC = setOf(FULL, PERCENT, VALUE)

    // Arknights sanity recovers one point every 360 seconds, the rate the reader already uses for the current value.
    private const val ARKNIGHTS_SANITY_MILLIS = 360_000L

    // Community-documented Endfield rate of one point every 432 seconds. It is not promised by the API, so it only
    // decides when to check; percentage and value rules need fresh data to be met.
    private const val ENDFIELD_SANITY_MILLIS = 432_000L

    private val all = listOf(
        ReminderCapability("arknights.sanity", "理智", NUMERIC, NUMERIC, ReminderTiming.REACH_TIME,
            RecoveryModel.FixedRate(ARKNIGHTS_SANITY_MILLIS), ReminderTexts("理智已满", "理智已达到 {P}%", "理智已达到 {T}")),
        ReminderCapability("arknights.drones", "无人机", NUMERIC, NUMERIC, ReminderTiming.REACH_TIME,
            RecoveryModel.Linear, ReminderTexts("无人机已满", "无人机已达到 {P}%", "无人机已达到 {T}")),
        // The share of resting operators who have fully rested, not a mood percentage. The snapshot has no time yet; a
        // completion time added later would make it REACH_TIME.
        ReminderCapability("arknights.dormitory_rest", "休息进度", NUMERIC, emptySet(), ReminderTiming.NONE,
            RecoveryModel.None, ReminderTexts("宿舍休息已全部完成", "休息完成比例已达到 {P}%", "已有 {T} 名干员休息完成")),
        ReminderCapability("arknights.training", "训练室专精", setOf(EVENT), setOf(EVENT), ReminderTiming.REACH_TIME,
            RecoveryModel.None, ReminderTexts(event = "训练室专精训练已完成")),
        // Full means every slot is complete; its time is the latest finish time of the running slots.
        ReminderCapability("arknights.recruit", "公开招募", setOf(FULL), setOf(FULL), ReminderTiming.REACH_TIME,
            RecoveryModel.None, ReminderTexts(full = "公开招募已全部完成")),
        // Trading and manufacture values are estimates themselves; only the next output time is known.
        ReminderCapability("arknights.trading", "订单进度", NUMERIC, emptySet(), ReminderTiming.NEXT_OUTPUT,
            RecoveryModel.None, ReminderTexts("订单已满", "订单进度已达到 {P}%", "订单进度已达到 {T}")),
        ReminderCapability("arknights.manufacture", "制造进度", NUMERIC, emptySet(), ReminderTiming.NEXT_OUTPUT,
            RecoveryModel.None, ReminderTexts("制造站产物已满", "制造进度已达到 {P}%", "制造进度已达到 {T}")),
        ReminderCapability("endfield.sanity", "理智", NUMERIC, setOf(FULL), ReminderTiming.REACH_TIME,
            RecoveryModel.FixedRate(ENDFIELD_SANITY_MILLIS), ReminderTexts("理智已满", "理智已达到 {P}%", "理智已达到 {T}")),
        ReminderCapability("endfield.daily_activation", "活跃度", NUMERIC, emptySet(), ReminderTiming.NONE,
            RecoveryModel.None, ReminderTexts("活跃度已满", "活跃度已达到 {P}%", "活跃度已达到 {T}")),
        ReminderCapability("endfield.weekly_mission", "每周事务", NUMERIC, emptySet(), ReminderTiming.NONE,
            RecoveryModel.None, ReminderTexts("每周事务已完成", "每周事务已达到 {P}%", "每周事务已达到 {T}")),
        ReminderCapability("endfield.pass_level", "通行证", NUMERIC, emptySet(), ReminderTiming.NONE,
            RecoveryModel.None, ReminderTexts("通行证已满级", "通行证已达到 {P}%", "通行证已达到 {T} 级")),
    ).associateBy { it.metricId }

    val metricIds: Set<String> get() = all.keys

    operator fun get(metricId: String): ReminderCapability? = all[metricId]

    // Whether the metric offers the rule's mode at all, regardless of its current data.
    fun check(rule: ReminderRule): RuleCheck {
        val capability = all[rule.metricId] ?: return RuleCheck.UNKNOWN_METRIC
        return if (rule.mode in capability.modes) RuleCheck.VALID else RuleCheck.UNSUPPORTED_MODE
    }

    // [check], and whether the rule fits the metric's current effective limit [limit] (null when unknown), for saving
    // a rule against the latest data.
    fun checkAgainst(rule: ReminderRule, limit: Int?): RuleCheck {
        val check = check(rule)
        if (check != RuleCheck.VALID) return check
        val capability = all.getValue(rule.metricId)
        if (rule.mode !in capability.modesFor(limit)) return RuleCheck.NEEDS_LIMIT
        val range = capability.thresholdRange(rule.mode, limit)
        val threshold = rule.threshold
        return if (range == null || threshold == null || threshold in range) RuleCheck.VALID else RuleCheck.ABOVE_LIMIT
    }
}
