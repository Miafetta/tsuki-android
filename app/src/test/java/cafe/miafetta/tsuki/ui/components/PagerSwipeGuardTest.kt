package cafe.miafetta.tsuki.ui.components

import androidx.compose.ui.input.pointer.PointerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerSwipeGuardTest {
    private val slop = 24f

    // Same test as the pager's swipe override, so the guard decides on the very event the override would start paging.
    @Test fun axisFollowsThePagerSlopTest() {
        assertNull(dragAxis(0f, 0f, slop))
        assertNull(dragAxis(slop, -slop, slop))
        assertEquals(DragAxis.Horizontal, dragAxis(slop + 1f, 0f, slop))
        assertEquals(DragAxis.Horizontal, dragAxis(-(slop + 1f), slop, slop))
        assertEquals(DragAxis.Vertical, dragAxis(0f, -(slop + 1f), slop))
        // Past the slop on both axes the larger one decides; a tie is not sideways.
        assertEquals(DragAxis.Horizontal, dragAxis(slop + 3f, slop + 2f, slop))
        assertEquals(DragAxis.Vertical, dragAxis(slop + 2f, -(slop + 3f), slop))
        assertEquals(DragAxis.Vertical, dragAxis(slop + 2f, slop + 2f, slop))
    }

    @Test fun claimedPointerIsConsumedFromTheSidewaysDecisionOn() {
        val guard = PagerSwipeGuard()
        val pointer = PointerId(7L)
        assertFalse("Unclaimed pointers stay with the pager", guard.move(pointer, 100f, 0f, slop))
        guard.claim(pointer)
        assertFalse(guard.move(pointer, 10f, 2f, slop))
        assertNull(guard.axis(pointer))
        assertTrue(guard.move(pointer, 15f, 1f, slop))
        assertEquals(DragAxis.Horizontal, guard.axis(pointer))
        // Once sideways, later vertical travel does not hand the gesture to the list.
        assertTrue(guard.move(pointer, 0f, 200f, slop))
        assertEquals(DragAxis.Horizontal, guard.axis(pointer))
        guard.release(pointer)
        assertNull(guard.axis(pointer))
        assertFalse(guard.move(pointer, 30f, 0f, slop))
    }

    @Test fun verticalFirstIsNeverConsumed() {
        val guard = PagerSwipeGuard()
        val pointer = PointerId(3L)
        val other = PointerId(4L)
        guard.claim(pointer)
        assertFalse(guard.move(pointer, 5f, -30f, slop))
        assertEquals(DragAxis.Vertical, guard.axis(pointer))
        assertFalse(guard.move(pointer, 300f, 0f, slop))
        assertEquals(DragAxis.Vertical, guard.axis(pointer))
        assertNull(guard.axis(other))
    }
}
