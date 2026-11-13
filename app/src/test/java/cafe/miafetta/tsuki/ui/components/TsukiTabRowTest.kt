package cafe.miafetta.tsuki.ui.components

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test

class TsukiTabRowTest {
    // The widgets page row at 336dp: 5dp contour and gap, two tabs of 160.5dp, rounded to px as the layout rounds them.
    @Test fun pointerPositionTracksTabCentresAtAnyDensityAndClampsTheEnds() {
        for (density in listOf(1f, 2.75f, 3f, 3.5f)) {
            val contour = (5 * density).roundToInt().toFloat()
            val tab = (160.5f * density).roundToInt().toFloat()
            val gap = (5 * density).roundToInt().toFloat()
            for (position in listOf(0f, .25f, .5f, .75f, 1f))
                assertEquals(position, tabPointerPosition(contour + tab / 2 + position * (tab + gap), contour, tab, gap, 1f),
                    .0001f)
            // The contour and the area beyond the outer tab centres hold the end tabs.
            assertEquals(0f, tabPointerPosition(0f, contour, tab, gap, 1f), 0f)
            assertEquals(0f, tabPointerPosition(-80f, contour, tab, gap, 1f), 0f)
            assertEquals(1f, tabPointerPosition(contour * 2 + tab * 2 + gap, contour, tab, gap, 1f), 0f)
            assertEquals(1f, tabPointerPosition(5000f, contour, tab, gap, 1f), 0f)
        }
    }

    @Test fun slotsUseTheLayoutPxAndTheDockStretchPerPitch() {
        val slots = tabSlots(tab = 482, gap = 15, count = 2)
        assertEquals(497f, slots.pitch, 0f)
        assertEquals(482f, slots.indicator, 0f)
        assertEquals(1f, slots.last, 0f)
        assertEquals(DockSlots.maxStretch / DockSlots.pitch, slots.maxStretch / slots.pitch, 1e-6f)
        // The cap is reached at the same lag, counted in slots, as on the dock.
        val capLag = DockSlots.maxStretch / DockSlots.pitch
        assertEquals(slotStretch(capLag * .5f, 1f, DockSlots) / DockSlots.pitch,
            slotStretch(capLag * .5f, .5f, slots) / slots.pitch, 1e-6f)
        assertEquals(slots.maxStretch, slotStretch(capLag * 2, .5f, slots), 1e-3f)
    }
}
