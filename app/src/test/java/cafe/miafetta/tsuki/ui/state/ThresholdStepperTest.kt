package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import org.junit.Assert.*
import org.junit.Test

// "−" and "+" of a value threshold whose limit is unknown: only that threshold steps, each tap moves one step inside the
// range, and a button is disabled at its end.
class ThresholdStepperTest {
    private val manufacture = ReminderCapabilities["arknights.manufacture"]!!

    @Test fun onlyAValueWithoutAKnownLimitSteps() {
        assertEquals(ThresholdStepper(1..Int.MAX_VALUE), ThresholdStepper.of(manufacture, VALUE, null))
        // "0/0" is no limit either.
        assertEquals(ThresholdStepper(1..Int.MAX_VALUE), ThresholdStepper.of(manufacture, VALUE, 0))
        // A known limit has a slider instead; a percentage always has one; a full rule has no threshold.
        assertNull(ThresholdStepper.of(manufacture, VALUE, 24))
        assertNull(ThresholdStepper.of(manufacture, VALUE, 1))
        assertNull(ThresholdStepper.of(manufacture, PERCENT, null))
        assertNull(ThresholdStepper.of(manufacture, FULL, 24))
    }

    @Test fun eachTapIsOneStep() {
        val stepper = ThresholdStepper.of(manufacture, VALUE, null)!!
        assertEquals(6, stepper.stepped(5, true))
        assertEquals(4, stepper.stepped(5, false))
        assertEquals(2, stepper.stepped(1, true))
        // Kept inside the range, without wrapping around at the top.
        assertEquals(1, stepper.stepped(1, false))
        assertEquals(Int.MAX_VALUE, stepper.stepped(Int.MAX_VALUE, true))
    }

    @Test fun aButtonIsDisabledAtItsEnd() {
        val stepper = ThresholdStepper.of(manufacture, VALUE, null)!!
        assertFalse(stepper.canDecrease(1))
        assertTrue(stepper.canIncrease(1))
        assertTrue(stepper.canDecrease(2))
        assertTrue(stepper.canIncrease(5000))
        assertFalse(stepper.canIncrease(Int.MAX_VALUE))
        assertTrue(stepper.canDecrease(Int.MAX_VALUE))
    }
}
