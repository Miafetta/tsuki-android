package cafe.miafetta.tsuki.ui.glass

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.dp
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ChipShadowAnchorTest {
    // Only isAttached is read by the anchor; anything else would mean it started to depend on more.
    private fun coordinates(attached: () -> Boolean): LayoutCoordinates = Proxy.newProxyInstance(
        LayoutCoordinates::class.java.classLoader, arrayOf(LayoutCoordinates::class.java)) { _, method, _ ->
        if (method.name == "isAttached") attached() else throw UnsupportedOperationException(method.name)
    } as LayoutCoordinates

    @Test fun everyPlacementTellsTheRowAndTheLatestOneCounts() {
        val anchor = ChipShadowAnchor()
        var moves = 0
        anchor.moved = { moves++ }
        val first = coordinates { true }
        val second = coordinates { true }
        anchor.place("arknights", first)
        anchor.place("endfield", coordinates { true })
        anchor.place("arknights", second)
        assertEquals(3, moves)
        assertSame(second, anchor.coordinates("arknights"))
        assertNotNull(anchor.coordinates("endfield"))
        assertNull(anchor.coordinates("unknown"))
    }

    @Test fun aDetachedChipCastsNoShadow() {
        val anchor = ChipShadowAnchor()
        var attached = true
        anchor.place("arknights", coordinates { attached })
        assertNotNull(anchor.coordinates("arknights"))
        attached = false
        assertNull(anchor.coordinates("arknights"))
    }

    @Test fun theShadowReachesPastTheRowsOwnBounds() {
        // The row keeps 4dp above and 10dp below each chip, which is where the Miuix overscroll layer clips; the shadow
        // reaches further, which is why the row, not the chip, draws it.
        assertTrue(ChipShadowRadius > 10.dp)
        assertEquals(15.dp, ChipShadowRadius)
    }

    @Test fun attachedListsEveryAttachedChipAndSkipsDetachedOnes() {
        val anchor = ChipShadowAnchor()
        var secondAttached = true
        anchor.place("arknights", coordinates { true })
        anchor.place("endfield", coordinates { secondAttached })
        assertEquals(listOf("arknights", "endfield"), anchor.attached().map { it.first })
        secondAttached = false
        assertEquals(listOf("arknights"), anchor.attached().map { it.first })
    }

    @Test fun unselectedShadowOnlyFollowsScrollProgressWhenGlassIsEnabled() {
        assertEquals(1f, chipShadowStrength(selected = true, unselectedProgress = 0f, unselectedGlassEnabled = false), 0f)
        assertEquals(1f, chipShadowStrength(selected = true, unselectedProgress = .4f, unselectedGlassEnabled = true), 0f)
        assertEquals(0f, chipShadowStrength(selected = false, unselectedProgress = 1f, unselectedGlassEnabled = false), 0f)
        assertEquals(0f, chipShadowStrength(selected = false, unselectedProgress = 0f, unselectedGlassEnabled = true), 0f)
        assertEquals(0f, chipShadowStrength(selected = false, unselectedProgress = -1f, unselectedGlassEnabled = true), 0f)
        assertEquals(0f, chipShadowStrength(selected = false, unselectedProgress = Float.NaN, unselectedGlassEnabled = true), 0f)
        assertEquals(0f, chipShadowStrength(selected = false, unselectedProgress = Float.POSITIVE_INFINITY,
            unselectedGlassEnabled = true), 0f)
        assertEquals(.65f, chipShadowStrength(selected = false, unselectedProgress = .65f, unselectedGlassEnabled = true), 1e-6f)
        assertEquals(1f, chipShadowStrength(selected = false, unselectedProgress = 1.5f, unselectedGlassEnabled = true), 0f)
    }
}
