package cafe.miafetta.tsuki.ui.glass

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

// Glass is on for the shown page and for both pages of a running transition; a covered page has none.
class TransitionGlassTest {
    @After fun restore() {
        TransitionGlass.keep = true
    }

    @Test fun glassStaysOnThroughTransitions() {
        assertEquals(true, TransitionGlass.active(running = false, relativeDepth = 0f))
        assertEquals(false, TransitionGlass.active(running = false, relativeDepth = -1f))
        assertEquals(true, TransitionGlass.active(running = true, relativeDepth = 0f))
        assertEquals(true, TransitionGlass.active(running = true, relativeDepth = -0.4f))
        assertEquals(true, TransitionGlass.active(running = true, relativeDepth = 0.6f))
    }

    @Test fun theEarlierBehaviourTurnsGlassOffWhileATransitionRuns() {
        TransitionGlass.keep = false
        assertEquals(true, TransitionGlass.active(running = false, relativeDepth = 0f))
        assertEquals(false, TransitionGlass.active(running = true, relativeDepth = 0f))
        assertEquals(false, TransitionGlass.active(running = true, relativeDepth = -0.4f))
        assertEquals(false, TransitionGlass.active(running = false, relativeDepth = -1f))
    }
}
