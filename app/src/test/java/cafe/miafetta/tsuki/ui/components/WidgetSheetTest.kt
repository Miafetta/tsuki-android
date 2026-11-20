package cafe.miafetta.tsuki.ui.components

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults

class WidgetSheetTest {
    // HyperOS's add and edit sheets keep the settings text 25.5dp from the screen edge and highlight a pressed row across
    // the whole sheet: the rows span the sheet and keep the inset inside, above and below as BasicComponent does.
    @Test fun settingsRowsSpanTheSheetAndKeepTheirText() {
        val margin = sheetRowMargin()
        for (direction in LayoutDirection.entries) {
            assertEquals(WidgetSheetInset, margin.calculateLeftPadding(direction))
            assertEquals(WidgetSheetInset, margin.calculateRightPadding(direction))
        }
        assertEquals(BasicComponentDefaults.InsideMargin.calculateTopPadding(), margin.calculateTopPadding())
        assertEquals(BasicComponentDefaults.InsideMargin.calculateBottomPadding(), margin.calculateBottomPadding())
        assertEquals(25.5f, WidgetSheetInset.value, .5f)
    }
}
