package cafe.miafetta.tsuki.core

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WidgetPinRequestTest {
    private val file = Files.createTempDirectory("widget-pin").resolve("pending.properties").toFile()
    private val store = WidgetPinRequest(file)
    private val config = WidgetInstanceConfig(1, "skland", "arknights.status", "account", "role", WidgetTheme.LIGHT)
    private fun prepare(token: String = "first") = store.prepare(token, config, setOf(10), 1000, 2)

    @Test fun exactSingleNewMatchingInstanceSurvivesProcessRecreation() {
        prepare()
        val reopened = WidgetPinRequest(file)
        val owned = mapOf(10 to WidgetTheme.LIGHT, 11 to WidgetTheme.LIGHT)
        assertEquals(config.copy(widgetId = 11), reopened.candidate(owned, emptySet(), 2000, 2))
        reopened.clear("first")
        assertFalse(reopened.pending)
    }

    @Test fun previousIdsWrongThemeAndConfiguredInstancesAreNeverRebound() {
        prepare()
        assertNull(store.candidate(mapOf(10 to WidgetTheme.LIGHT, 11 to WidgetTheme.DARK, 12 to WidgetTheme.LIGHT), setOf(12), 2000, 2))
        assertTrue(store.pending)
    }

    @Test fun ambiguousPlacementIsDiscardedRatherThanPickingAnAccount() {
        prepare()
        assertNull(store.candidate(mapOf(11 to WidgetTheme.LIGHT, 12 to WidgetTheme.LIGHT), emptySet(), 2000, 2))
        assertFalse(store.pending)
        assertNull(store.candidate(mapOf(11 to WidgetTheme.LIGHT), emptySet(), 3000, 2))
    }

    @Test fun expiredAndRebootedRequestsCannotConfigureLaterWidgets() {
        prepare()
        assertNull(store.candidate(mapOf(11 to WidgetTheme.LIGHT), emptySet(), 121001, 2))
        assertFalse(store.pending)
        prepare()
        assertNull(store.candidate(mapOf(11 to WidgetTheme.LIGHT), emptySet(), 2000, 3))
        assertFalse(store.pending)
    }

    @Test fun staleCallbackCannotClearANewerPlacement() {
        prepare()
        prepare("second")
        store.clear("first")
        assertTrue(store.pending)
        store.clear("second")
        assertFalse(store.pending)
    }

    @Test fun returningToAppCancelsUnconsumedPlacement() {
        prepare()
        store.clear()
        assertNull(store.candidate(mapOf(11 to WidgetTheme.LIGHT), emptySet(), 2000, 2))
    }

    @Test fun resumeCleanupCannotCancelAPlacementStartedAfterThatResume() {
        prepare()
        store.clear(before = 1000)
        assertTrue(store.pending)
        store.clear(before = 2000)
        assertFalse(store.pending)
    }
}
