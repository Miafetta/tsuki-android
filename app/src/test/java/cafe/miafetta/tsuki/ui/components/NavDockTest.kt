package cafe.miafetta.tsuki.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NavDockTest {
    @Test fun pointerPositionTracksContinuouslyAtAnyDensityAndClampsTheEnds() {
        for (density in listOf(1f, 3.25f, 3.3333333f)) {
            for (position in listOf(0f, .25f, .5f, 1f, 1.75f, 2f))
                assertEquals(position, dockPointerPosition((44.5f + position * 73f) * density, 235f * density), .0001f)
            assertEquals(0f, dockPointerPosition(-20f, 235f * density), 0f)
            assertEquals(2f, dockPointerPosition(255f * density, 235f * density), 0f)
        }
    }
}
