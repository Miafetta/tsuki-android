package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderCapability
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import kotlin.math.roundToInt

// The slider of a percentage or value threshold: its track is the mode's valid range (1 to 100 percent, or 1 to the
// known limit), one whole step per value. Under the start of the track reads the value being chosen, under its end the
// end of the range, both written as ReminderOptions.thresholdText writes thresholds. [steps] counts the values strictly
// between the ends, as the Miuix slider takes them.
internal data class ThresholdSlider(val range: IntRange, val mode: ReminderMode) {
    val steps get() = range.last - range.first - 1

    // A tick on every value, so each step can be felt; no key points, since with key points Miuix ticks only at them.
    // With no value between the ends (a range of two) the slider has no steps and reports positions in between while
    // dragging, so a tick per step would fire on every move; only the two ends are felt then.
    val stepHaptics get() = steps > 0

    // The label under the end of the track: "100%", or the limit.
    val endLabel get() = ReminderOptions.thresholdText(mode, range.last)

    // The label under the start of the track: the value being chosen, which follows a drag.
    fun valueLabel(value: Int) = ReminderOptions.thresholdText(mode, value)

    // The whole value at a slider position.
    fun valueAt(position: Float): Int = position.roundToInt().coerceIn(range)

    companion object {
        // Null when there is no track to draw: a value threshold without a known limit has no upper end (its row steps
        // instead, ThresholdStepper), and a limit of one leaves a single value.
        fun of(capability: ReminderCapability, mode: ReminderMode, limit: Int?): ThresholdSlider? {
            val range = capability.thresholdRange(mode, limit) ?: return null
            if (range.last == Int.MAX_VALUE || range.last <= range.first) return null
            return ThresholdSlider(range, mode)
        }
    }
}
