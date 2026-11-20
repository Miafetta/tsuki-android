package cafe.miafetta.tsuki.ui.sheets

import cafe.miafetta.tsuki.ui.state.UiWidget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigEditorTest {
    // 全部小部件 opens the sheet without an instance (no widget ID: -1 by default, AppWidgetManager's invalid 0 from the app);
    // every placed instance, configured or still unconfigured, is edited.
    @Test fun onlyASheetWithoutAnInstanceAddsANewWidget() {
        val placed = UiWidget(42, "account", "role", "skland", "LIGHT", null, "", false)
        assertTrue(isNewWidget(null, -1))
        assertTrue(isNewWidget(null, 0))
        assertFalse(isNewWidget(null, 42))
        assertFalse(isNewWidget(placed, 42))
        assertFalse(isNewWidget(placed, -1))
    }
}
