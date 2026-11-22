package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.navCoveredTranslationX
import cafe.miafetta.tsuki.ui.navSlideTranslationX
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

    @Test fun navTransitionsSnapSlidingEdgesToWholePixelsAndPreserveCoveredBounds() {
        assertEquals(0f, navSlideTranslationX(0f, 1200f, rtl = false), 0f)
        assertEquals(1200f, navSlideTranslationX(-1f, 1200f, rtl = false), 0f)
        assertEquals(400f, navSlideTranslationX(-1f / 3f, 1200f, rtl = false), 0f)
        assertEquals(600f, navSlideTranslationX(-0.5002f, 1200f, rtl = false), 0f)
        assertEquals(-600f, navSlideTranslationX(-0.5f, 1200f, rtl = true), 0f)
        assertEquals(0f, navSlideTranslationX(Float.NaN, 1200f, rtl = false), 0f)
        assertEquals(0f, navCoveredTranslationX(0f, 1200f, rtl = false), 0f)
        assertEquals(-300f, navCoveredTranslationX(1f, 1200f, rtl = false), 0f)
        assertEquals(-150f, navCoveredTranslationX(0.5f, 1200f, rtl = false), 0f)
        assertEquals(150f, navCoveredTranslationX(0.5f, 1200f, rtl = true), 0f)
        assertEquals(0f, navCoveredTranslationX(0.5f, Float.POSITIVE_INFINITY, rtl = false), 0f)
    }
}
