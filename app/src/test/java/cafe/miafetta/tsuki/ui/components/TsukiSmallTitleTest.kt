package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.TsukiTextStyles
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TsukiSmallTitleTest {
    private val em = 12.dp
    private fun inkTop(top: Dp) = top + em * LineAboveInkEm
    private fun inkBottom(bottom: Dp) = bottom + em * LineBelowInkEm

    // HyperOS Settings' section title: ink 21.1dp below the card above and 10.1dp above the card below, whatever the list
    // in between adds; 9.6dp below the start of the page when it opens the page.
    @Test fun titleInkSitsWhereTheSystemPutsIt() {
        for (spacing in listOf(0.dp, 8.dp, PageListSpacing)) {
            assertEquals(21.1f, (spacing + inkTop(smallTitleTop(em, spacing, first = false))).value, .001f)
        }
        assertEquals(9.6f, inkTop(smallTitleTop(em, PageListSpacing, first = true)).value, .001f)
        assertEquals(10.1f, inkBottom(smallTitleBottom(em, 0.dp)).value, .001f)
        // A card that is the next list item starts inside the title's line box, still 10.1dp under the ink.
        val bottom = smallTitleBottom(em, PageListSpacing)
        assertTrue(bottom.value < 0f)
        assertEquals(10.1f, (inkBottom(bottom) + PageListSpacing).value, .001f)
    }

    @Test fun mainPageTitlePaddings() {
        assertEquals(8.82f, smallTitleTop(em, PageListSpacing, first = false).value, .01f)
        assertEquals(7.58f, smallTitleBottom(em, 0.dp).value, .01f)
        assertEquals(0f, smallTitleTop(em, 40.dp, first = false).value, 0f)
    }

    // The system title advances 40 px per CJK glyph on the 3.25 px/dp phone (12.3sp); footnote1 is the nearest style.
    @Test fun titleTakesTheNearestTextStyle() {
        val measured = 40 / 3.25f
        val styles = with(TsukiTextStyles) {
            listOf(title1, title2, title3, title4, headline1, headline2, subtitle, main, button, paragraph, body1, body2,
                footnote1, footnote2)
        }
        val nearest = styles.minBy { abs(it.fontSize.value - measured) }
        assertEquals(TsukiTextStyles.footnote1.fontSize, nearest.fontSize)
        assertEquals(12f, TsukiTextStyles.footnote1.fontSize.value, 0f)
    }
}
