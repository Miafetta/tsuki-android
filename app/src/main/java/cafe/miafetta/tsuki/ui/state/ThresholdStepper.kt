package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderCapability
import cafe.miafetta.tsuki.core.reminder.ReminderMode

// The "−" and "+" of a value threshold whose limit is unknown (a capacity the snapshot does not give): the one
// threshold with no track for a slider, since its range has no upper end. Each tap moves one whole step, and a button
// is disabled at its end of the range.
internal data class ThresholdStepper(val range: IntRange) {
    fun canDecrease(value: Int) = value > range.first

    fun canIncrease(value: Int) = value < range.last

    // One step down or up, kept inside the range. Counted in Long so a step at Int.MAX_VALUE cannot wrap around.
    fun stepped(value: Int, up: Boolean): Int =
        (value.toLong() + if (up) 1 else -1).coerceIn(range.first.toLong(), range.last.toLong()).toInt()

    companion object {
        fun of(capability: ReminderCapability, mode: ReminderMode, limit: Int?): ThresholdStepper? =
            capability.thresholdRange(mode, limit)?.takeIf { it.last == Int.MAX_VALUE }?.let(::ThresholdStepper)
    }
}
