package cafe.miafetta.tsuki.ui.components

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotMotionTest {
    private val samples = listOf(0f, .01f, .3f, .5f, .999f, 1f, 1.37f, 1.5f, 1.99f, 2f)

    // The dock's calibrated motion now runs through the shared functions; they must give its former inline arithmetic
    // exactly, not just closely.
    @Test fun dockArithmeticIsUnchanged() {
        for (position in samples) {
            for (target in samples) {
                val lag = abs(target - position)
                assertEquals(minOf(lag * 73f, 18f, position * 146f, (2f - position) * 146f).coerceAtLeast(0f),
                    slotStretch(lag, position, DockSlots), 0f)
            }
            for (stretch in listOf(0f, 4.5f, 18f, 40f))
                assertEquals(minOf(stretch, position * 146f, (2f - position) * 146f).coerceAtLeast(0f) / 83f,
                    stretchScale(stretch, position, DockSlots), 0f)
        }
        for (width in listOf(235f, 646.25f, 783.3333f)) {
            for (dx in listOf(-900f, -40.5f, 0f, 13f, 222.2f, 900f))
                assertEquals((1f + dx / (width * 73f / 235f)).coerceIn(0f, 2f),
                    dragSlotPosition(1f, dx, width * 73f / 235f, 2f), 0f)
            for (x in listOf(-20f, 0f, 104.6f, width / 2, width - 1f, width + 20f))
                assertEquals(((x / width * 235f - 44.5f) / 73f).coerceIn(0f, 2f), dockPointerPosition(x, width), 0f)
        }
    }

    @Test fun stretchGrowsWithLagUpToItsCapAndStaysInsideTheTravel() {
        val slots = SlotGeometry(pitch = 100f, indicator = 90f, last = 2f, maxStretch = 25f)
        assertEquals(10f, slotStretch(.1f, 1f, slots), 1e-4f)
        assertEquals(25f, slotStretch(1f, 1f, slots), 0f)
        // At either end there is no room to stretch; .1 slot from the start leaves twice 10 px.
        assertEquals(0f, slotStretch(1f, 0f, slots), 0f)
        assertEquals(0f, slotStretch(1f, 2f, slots), 0f)
        assertEquals(20f, slotStretch(1f, .1f, slots), 1e-4f)
        // An animated stretch that outlives its position is limited again where the indicator is now.
        assertEquals(20f / 90f, stretchScale(25f, 1.9f, slots), 1e-4f)
        assertEquals(25f / 90f, stretchScale(25f, 1f, slots), 1e-6f)
    }

    @Test fun dragFollowsTheFingerAndClampsToTheRow() {
        assertEquals(.5f, dragSlotPosition(0f, 50f, 100f, 1f), 0f)
        assertEquals(1f, dragSlotPosition(0f, 400f, 100f, 1f), 0f)
        assertEquals(0f, dragSlotPosition(1f, -400f, 100f, 1f), 0f)
        assertEquals(.25f, slotPosition(75f, 50f, 100f, 1f), 0f)
        assertEquals(0f, slotPosition(-10f, 50f, 100f, 1f), 0f)
    }
}
