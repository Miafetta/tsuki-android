package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.ReminderObservations
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderCapability
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.providers.isArknightsSource

// What the reminder settings offer for each metric. Names come from the capability registry; only the page order, the
// wording of the two single-mode switches, the condition menu's texts and the threshold row's words are added here.
internal object ReminderOptions {
    const val DEFAULT_PERCENT = 80

    // The first entry of a metric's condition menu, which turns its reminder off.
    const val OFF = "关闭"

    // The title of the threshold row of a value without a slider, and the accessibility names of its "−" and "+"
    // (ThresholdStepper).
    const val THRESHOLD = "阈值"
    const val DECREASE = "减少"
    const val INCREASE = "增加"

    // Appended to the metric name for the metrics with a single mode, whose switch has no condition to choose.
    private val switchSuffixes = mapOf("arknights.training" to "训练完成", "arknights.recruit" to "全部完成")

    // The rows of each game's role card in page order. ReminderOptionsTest checks that they hold every metric of [metrics]
    // exactly once, so a newly registered metric has to be placed on purpose.
    private val rowOrder = mapOf(
        SklandGame.ARKNIGHTS to listOf("arknights.sanity", "arknights.drones", "arknights.dormitory_rest", "arknights.trading",
            "arknights.manufacture", "arknights.training", "arknights.recruit"),
        SklandGame.ENDFIELD to listOf("endfield.sanity", "endfield.daily_activation", "endfield.weekly_mission",
            "endfield.pass_level"))

    // The metrics of [game] in registry order. Metric IDs start with the game's app code ("arknights.", "endfield."),
    // the same test the widget process applies when a rule is saved.
    fun metrics(game: SklandGame): List<ReminderCapability> =
        ReminderCapabilities.metricIds.filter { it.substringBefore('.') == game.appCode }.mapNotNull { ReminderCapabilities[it] }

    fun rows(game: SklandGame): List<ReminderCapability> = rowOrder[game].orEmpty().mapNotNull { ReminderCapabilities[it] }

    fun isSwitch(capability: ReminderCapability) = capability.modes.size == 1

    fun title(capability: ReminderCapability) =
        if (isSwitch(capability)) capability.name + switchSuffixes[capability.metricId].orEmpty() else capability.name

    // The conditions a metric offers: the modes its current limit allows. A stored rule whose mode the limit no longer
    // allows (its snapshot is gone, say) keeps its entry, so the page still shows what is saved.
    fun conditions(capability: ReminderCapability, limit: Int?, stored: ReminderMode? = null): List<ReminderMode> {
        val allowed = capability.modesFor(limit)
        return ReminderMode.entries.filter { it != ReminderMode.EVENT && it in capability.modes && (it in allowed || it == stored) }
    }

    // A metric's condition menu: "关闭" (null) first, then its conditions, with the enabled rule's [stored] condition
    // listed even when the current limit no longer allows it.
    fun choices(capability: ReminderCapability, limit: Int?, stored: ReminderMode?): List<ReminderMode?> =
        listOf<ReminderMode?>(null) + conditions(capability, limit, stored)

    fun choiceLabel(mode: ReminderMode?) = mode?.let(::conditionLabel) ?: OFF

    // Whether the threshold of [mode] can be changed. A stored condition the current limit no longer allows (listed by
    // conditions) can only be kept as it is: the widget process refuses to save it against the current limit
    // (ReminderCapabilities.checkAgainst), so its threshold row is shown disabled.
    fun adjustable(capability: ReminderCapability, mode: ReminderMode, limit: Int?) = mode in capability.modesFor(limit)

    // The rule a choice leaves: none for "关闭" (null), so the stored rule is deleted; else the rule with [mode] (a
    // condition from the menu, or the single mode of a switch) and the threshold [previous] that condition had before in
    // this session while it still fits the range (a value above a limit that has since dropped does not), else the
    // condition's default.
    fun chosen(subject: ReminderSubject, capability: ReminderCapability, mode: ReminderMode?, previous: Int?,
        limit: Int?): ReminderRule? {
        mode ?: return null
        val range = capability.thresholdRange(mode, limit)
        val threshold = range?.let { previous?.takeIf(it::contains) ?: defaultThreshold(mode, limit) }
        return ReminderRule(subject, capability.metricId, mode, threshold)
    }

    fun conditionLabel(mode: ReminderMode) = when (mode) {
        ReminderMode.FULL -> "已满时提醒"
        ReminderMode.PERCENT -> "达到百分比时提醒"
        ReminderMode.VALUE -> "达到数值时提醒"
        // Never listed: an event metric has a plain switch.
        ReminderMode.EVENT -> "完成时提醒"
    }

    // The threshold a mode starts with when it is chosen: 80 percent, or the whole current limit (1 while unknown).
    fun defaultThreshold(mode: ReminderMode, limit: Int?): Int? = when (mode) {
        ReminderMode.PERCENT -> DEFAULT_PERCENT
        ReminderMode.VALUE -> limit?.takeIf { it > 0 } ?: ReminderCapabilities.MIN_VALUE
        ReminderMode.FULL, ReminderMode.EVENT -> null
    }

    // A threshold as the page writes it, under a slider or in a "阈值" row: "80%", or the value alone ("160"). The
    // limit is not repeated: the slider names it under the end of its track.
    fun thresholdText(mode: ReminderMode, threshold: Int): String =
        if (mode == ReminderMode.PERCENT) "$threshold%" else "$threshold"

    // Each metric's effective limit in the role's latest snapshot, read the way the reminder checks read it. Metrics
    // without a reader yet (or missing from the snapshot) map to null, which the page treats as an unknown limit.
    fun limits(subject: ReminderSubject, snapshot: WidgetSnapshot?, metrics: List<ReminderCapability>): Map<String, Int?> {
        if (snapshot == null) return emptyMap()
        val arknights = isArknightsSource(subject.providerId)
        return metrics.associate { capability ->
            capability.metricId to try {
                ReminderObservations.from(subject, capability.metricId, snapshot, false, arknights)?.limit
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
