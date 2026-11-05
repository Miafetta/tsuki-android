package cafe.miafetta.tsuki.widget

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WidgetTargetsTest {
    private val owned = intArrayOf(10, 20)

    @Test fun missingIdsRefreshAllOwnedWidgets() {
        assertArrayEquals(owned, WidgetTargets.resolve(owned, null))
    }

    @Test fun emptyIdsDoNotRefreshAllWidgets() {
        assertArrayEquals(intArrayOf(), WidgetTargets.resolve(owned, intArrayOf()))
    }

    @Test fun foreignIdsAndDuplicatesAreExcluded() {
        assertArrayEquals(intArrayOf(20), WidgetTargets.resolve(owned, intArrayOf(30, 20, 20, -1)))
    }

    @Test fun manualRefreshOnlyTargetsOneInstance() {
        assertArrayEquals(intArrayOf(10), WidgetTargets.resolve(owned, null, 10))
    }

    @Test fun foreignSingleIdDoesNotRefreshOtherInstances() {
        assertArrayEquals(intArrayOf(), WidgetTargets.resolve(owned, null, 30))
    }

    @Test fun arrayTakesPrecedenceOverSingleId() {
        assertArrayEquals(intArrayOf(20), WidgetTargets.resolve(owned, intArrayOf(20), 10))
    }

    @Test fun noInstalledWidgetsProducesNoTargets() {
        assertArrayEquals(intArrayOf(), WidgetTargets.resolve(intArrayOf(), null))
    }
}
