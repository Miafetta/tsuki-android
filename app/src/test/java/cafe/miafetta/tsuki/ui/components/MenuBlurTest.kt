package cafe.miafetta.tsuki.ui.components

import org.junit.Assert.*
import org.junit.Test

class MenuBlurTest {
    @Test fun stableEndpointsHaveNoEffectAndTransitionNeverExceedsSixDp() {
        assertEquals(0f, menuContentBlurRadius(0f), 0f)
        assertEquals(0f, menuContentBlurRadius(1f), 0f)
        for (step in 1..99) {
            val visibility = step / 100f
            assertEquals(6f * (1f - visibility), menuContentBlurRadius(visibility), .00001f)
            assertTrue(menuContentBlurRadius(visibility) in 0f..6f)
        }
    }
}
