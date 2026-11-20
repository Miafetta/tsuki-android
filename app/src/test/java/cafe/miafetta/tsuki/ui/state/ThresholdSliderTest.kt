package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.*
import org.junit.Test

// The threshold slider: its track is the valid range in whole steps, the value being chosen reads under its start and
// the end of the range under its end, every value ticks except on a range of two, a position reads as a whole value
// inside the range, and when there is no slider at all.
class ThresholdSliderTest {
    private val sanity = ReminderCapabilities["arknights.sanity"]!!

    @Test fun percentSpansOneToHundredInWholeSteps() {
        val slider = ThresholdSlider(1..100, PERCENT)
        assertEquals(slider, ThresholdSlider.of(sanity, PERCENT, 135))
        // A saved percentage rule keeps its slider while the limit is unknown: the range does not depend on it.
        assertEquals(slider, ThresholdSlider.of(sanity, PERCENT, null))
        assertEquals(98, slider.steps)
        assertTrue(slider.stepHaptics)
    }

    // The track is the valid range itself (ReminderCapability.thresholdRange), so a value cannot be dragged to 0.
    @Test fun valueSpansOneToTheLimit() {
        val slider = ThresholdSlider.of(sanity, VALUE, 210)!!
        assertEquals(ThresholdSlider(1..210, VALUE), slider)
        assertEquals(sanity.thresholdRange(VALUE, 210), slider.range)
        assertEquals(208, slider.steps)
        assertTrue(slider.stepHaptics)
        assertEquals(ThresholdSlider(1..3, VALUE), ThresholdSlider.of(sanity, VALUE, 3))
        assertEquals(1, ThresholdSlider.of(sanity, VALUE, 3)!!.steps)
    }

    // Under the start the value being chosen, which follows a drag; under the end the end of the range.
    @Test fun theLabelsAreTheValueAndTheEnd() {
        val percent = ThresholdSlider.of(sanity, PERCENT, 135)!!
        assertEquals("80%", percent.valueLabel(80))
        assertEquals("64%", percent.valueLabel(64))
        assertEquals("100%", percent.endLabel)
        val value = ThresholdSlider.of(sanity, VALUE, 210)!!
        assertEquals("160", value.valueLabel(160))
        assertEquals("210", value.endLabel)
        // A stored value above a limit that has since dropped reads as it is stored, beside the new end.
        val dropped = ThresholdSlider.of(sanity, VALUE, 135)!!
        assertEquals("160", dropped.valueLabel(160))
        assertEquals("135", dropped.endLabel)
    }

    // Two values leave no step in between: the slider takes 0 steps and is felt at its ends only.
    @Test fun aRangeOfTwoIsFeltAtItsEndsOnly() {
        val two = ThresholdSlider.of(sanity, VALUE, 2)!!
        assertEquals(ThresholdSlider(1..2, VALUE), two)
        assertEquals(0, two.steps)
        assertFalse(two.stepHaptics)
    }

    @Test fun positionsReadAsWholeValuesInsideTheRange() {
        val percent = ThresholdSlider.of(sanity, PERCENT, null)!!
        assertEquals(80, percent.valueAt(80f))
        assertEquals(80, percent.valueAt(79.6f))
        assertEquals(80, percent.valueAt(80.4f))
        assertEquals(1, percent.valueAt(0f))
        assertEquals(100, percent.valueAt(100.7f))
        val value = ThresholdSlider.of(sanity, VALUE, 2)!!
        // The range of two reports positions in between; each reads as the nearer end.
        assertEquals(1, value.valueAt(1.4f))
        assertEquals(2, value.valueAt(1.6f))
    }

    // Without a slider the row steps (an unknown limit, ThresholdStepper), shows its single value, or has no threshold.
    @Test fun noSliderWithoutABoundedRangeOfTwoValues() {
        assertNull(ThresholdSlider.of(sanity, VALUE, null))
        assertNull(ThresholdSlider.of(sanity, VALUE, 0))
        assertNull(ThresholdSlider.of(sanity, VALUE, 1))
        assertNull(ThresholdSlider.of(sanity, FULL, 135))
        assertNull(ThresholdSlider.of(ReminderCapabilities["arknights.training"]!!, ReminderMode.EVENT, null))
    }

    // Every percentage and value metric gets the same slider: the range of its mode, labelled with the value and the end,
    // with a tick per value.
    @Test fun everyMetricLabelsTheEndOfItsRange() {
        val metrics = SklandGame.entries.flatMap { ReminderOptions.metrics(it) }
        for (capability in metrics.filter { PERCENT in it.modes }) {
            val percent = ThresholdSlider.of(capability, PERCENT, 10)!!
            assertEquals(capability.metricId, ThresholdSlider(1..100, PERCENT), percent)
            assertEquals(capability.metricId, "100%", percent.endLabel)
            val value = ThresholdSlider.of(capability, VALUE, 10)!!
            assertEquals(capability.metricId, ThresholdSlider(1..10, VALUE), value)
            assertEquals(capability.metricId, "10", value.endLabel)
            assertTrue(capability.metricId, percent.stepHaptics && value.stepHaptics)
        }
    }
}
