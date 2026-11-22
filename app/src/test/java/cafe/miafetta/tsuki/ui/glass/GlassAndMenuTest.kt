package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.theme.TsukiTextStyles
import cafe.miafetta.tsuki.ui.components.MenuCloseMillis
import cafe.miafetta.tsuki.ui.components.MenuMotion
import cafe.miafetta.tsuki.ui.components.MenuOpenMillis
import cafe.miafetta.tsuki.ui.components.TsukiMenuPosition
import cafe.miafetta.tsuki.ui.components.menuMotion
import cafe.miafetta.tsuki.ui.components.FieldMenuCloseMillis
import cafe.miafetta.tsuki.ui.components.FieldMenuCollapseMillis
import cafe.miafetta.tsuki.ui.components.FieldMenuOpenMillis
import cafe.miafetta.tsuki.ui.components.FieldStartWidth
import cafe.miafetta.tsuki.ui.components.FieldTriggerFadeOutMillis
import cafe.miafetta.tsuki.ui.components.fieldMenuMotion

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test
import top.yukonga.miuix.kmp.theme.defaultTextStyles

class GlassAndMenuTest {
    @Test fun onlyActualFallbackConsumesBackdrop() {
        assertEquals("miuix-blur", glassBackend(true, true, false, true))
        assertEquals("MiGlass", glassBackend(true, true, true, true))
        assertEquals("opaque", glassBackend(false, true, false, true))
        assertEquals("inactive", glassBackend(true, false, false, true))
        assertTrue(glassPendingNative(enabled = true, reported = null))
        assertFalse(glassPendingNative(enabled = true, reported = true))
        assertFalse(glassPendingNative(enabled = true, reported = false))
        assertFalse(glassPendingNative(enabled = false, reported = null))
        assertFalse(glassConsumerNeedsBackdrop("miuix-blur", pendingNative = true))
        assertTrue(glassConsumerNeedsBackdrop("miuix-blur", pendingNative = false))
        assertFalse(glassConsumerNeedsBackdrop("MiGlass", pendingNative = false))
    }

    @Test fun menuMorphStaysBoundedAndContinuous() {
        assertEquals(MenuMotion(.24f, .45f, 0f), menuMotion(0f))
        assertEquals(MenuMotion(1f, 1f, 0f), menuMotion(1f))
        assertEquals(menuMotion(0f), menuMotion(Float.NaN))
        assertEquals(menuMotion(0f), menuMotion(-1f))
        assertEquals(menuMotion(1f), menuMotion(2f))
        val samples = (0..330).map { menuMotion(it / 330f) }
        assertTrue(samples.all { it.width in .24f..1f && it.height in .45f..1f && it.offset in 0f..1f })
        assertTrue(samples.zipWithNext().all { (a, b) ->
            b.width >= a.width && b.height >= a.height &&
                b.width - a.width < .01f && kotlin.math.abs(b.offset - a.offset) < .02f
        })
        assertTrue(MenuCloseMillis < MenuOpenMillis)
    }

    @Test fun glassScrollAndFadeHaveContinuousFlatEndpoints() {
        assertEquals(0f, glassScrollProgress(0f), 0f)
        assertEquals(.5f, glassScrollProgress(1f / 6f), .0001f)
        assertEquals(1f, glassScrollProgress(1f / 3f), 0f)
        assertEquals(1f, glassScrollProgress(0f, 1), 0f)
        assertEquals(0f, glassScrollProgress(Float.NaN), 0f)
        assertTrue(glassScrollProgress(1f / 3f - .001f) > .999f)
        val samples = (0..100).map { glassEase(it / 100f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a && b - a < .016f })
        assertTrue(glassEase(.001f) < .00001f)
        assertTrue(1f - glassEase(.999f) < .00001f)
        assertEquals(0f, glassEase(-1f), 0f)
        assertEquals(1f, glassEase(2f), 0f)
    }

    @Test fun glassRequiresAllCapabilities() {
        for (enabled in listOf(false, true)) for (hardware in listOf(false, true)) for (supported in listOf(false, true))
            assertEquals(enabled && hardware && supported, glassAvailable(enabled, hardware, supported))
    }

    @Test fun glassAppearsAtTheSmallTitleThresholdEvenWithoutListMovement() {
        assertFalse(glassVisible(0f))
        assertFalse(glassVisible(1f / 3f - .0001f))
        assertTrue(glassVisible(1f / 3f))
        assertTrue(glassVisible(1f))
        assertTrue(glassVisible(0f, 1))
        assertFalse(glassVisible(Float.NaN))
    }

    @Test fun topGlassClearsEightDpBelowTheChipsOrTheActionRow() {
        // GameChips: 4dp padding, a 38dp chip, 10dp padding in a 52dp slot below the action row.
        assertEquals(52.dp, GameChipSlot)
        assertEquals(4.dp + 38.dp, GameChipBottom)
        assertEquals(GameChipSlot, GameChipBottom + 10.dp)
        assertEquals(8.dp, TopGlassFade)
        assertEquals(GameChipBottom + 8.dp, GameHeaderFade)
        assertEquals(50.dp, GameHeaderFade)
    }

    @Test fun glassFractionsStayInsideTheGlass() {
        val collapsed = 92f
        assertEquals(collapsed / (collapsed + 50f), glassFullStrengthFraction(collapsed + GameHeaderFade.value, collapsed), .0001f)
        assertEquals(0f, glassFullStrengthFraction(0f, collapsed), 0f)
        assertEquals(0f, glassFullStrengthFraction(-10f, collapsed), 0f)
        assertEquals(1f, glassFullStrengthFraction(20f, collapsed), 0f)
        assertEquals(0f, glassFullStrengthFraction(100f, -5f), 0f)
        assertEquals(0f, glassFullStrengthFraction(Float.NaN, collapsed), 0f)
        assertEquals(0f, glassFullStrengthFraction(100f, Float.NaN), 0f)
        assertEquals(0f, glassFullStrengthFraction(100f, Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun popupOverlapsAnchorAndClampsAllWindowEdges() {
        val window = IntSize(360, 800)
        fun place(anchor: IntRect, right: Boolean, size: IntSize = IntSize(184, 160)) =
            TsukiMenuPosition(right, IntRect(8, 24, 8, 40)).calculatePosition(anchor, window, LayoutDirection.Ltr, size)
        assertEquals(IntOffset(140, 80), place(IntRect(284, 80, 324, 120), true))
        assertEquals(IntOffset(74, 180), place(IntRect(74, 180, 160, 220), false))
        assertEquals(IntOffset(140, 580), place(IntRect(284, 700, 324, 740), true))
        assertEquals(IntOffset(8, 24), place(IntRect(-20, -20, 20, 20), true))
        assertEquals(IntOffset(168, 600), place(IntRect(350, 750, 400, 800), false))
        assertEquals(IntOffset(8, 24), place(IntRect(0, 0, 10, 10), false, IntSize(900, 900)))
    }

    @Test fun popupPaddingDoesNotMoveThePanelAnchorAndReportsOpeningCorner() {
        var above = false
        val position = TsukiMenuPosition(true, IntRect(8, 24, 8, 40), 24) { above = it }
        val size = IntSize(184 + 48, 160 + 48)
        fun at(anchor: IntRect, direction: LayoutDirection) = position.calculatePosition(anchor, IntSize(360, 800), direction, size)
        assertEquals(IntOffset(140 - 24, 80 - 24), at(IntRect(284, 80, 324, 120), LayoutDirection.Ltr))
        assertFalse(above)
        assertEquals(IntOffset(140 - 24, 580 - 24), at(IntRect(284, 700, 324, 740), LayoutDirection.Rtl))
        assertTrue(above)
        assertEquals(IntOffset(8 - 24, 24 - 24), at(IntRect(-20, -20, 20, 20), LayoutDirection.Ltr))
    }

    @Test fun fieldMenuGrowsFromAFixedPillAndSettles() {
        assertEquals(MenuMotion(FieldStartWidth, .2f, .15f), fieldMenuMotion(0f, .2f))
        assertEquals(MenuMotion(1f, 1f, 0f), fieldMenuMotion(1f, .2f))
        assertEquals(fieldMenuMotion(0f, .2f), fieldMenuMotion(Float.NaN, .2f))
        assertEquals(1f, fieldMenuMotion(0f, Float.NaN).height, 0f)
        val samples = (0..FieldMenuOpenMillis).map { fieldMenuMotion(it / FieldMenuOpenMillis.toFloat(), .17f) }
        assertTrue(samples.all { it.width in FieldStartWidth..1f && it.height in .17f..1f && it.offset in 0f..1f })
        assertTrue(samples.zipWithNext().all { (a, b) ->
            b.width >= a.width && b.height >= a.height && b.height - a.height < .02f && kotlin.math.abs(b.offset - a.offset) < .03f
        })
        assertTrue(FieldMenuCollapseMillis < FieldMenuCloseMillis && FieldTriggerFadeOutMillis < FieldMenuOpenMillis)
    }

    @Test fun fieldMenuReachesPastTheTriggerCentreAndReportsItInPopupCoordinates() {
        var above = false
        var anchor = IntRect.Zero
        val position = TsukiMenuPosition(true, IntRect(8, 24, 8, 40), 24, 14, { anchor = it }) { above = it }
        val size = IntSize(200 + 48, 100 + 48)
        val trigger = IntRect(200, 300, 312, 320)
        assertEquals(IntOffset(112 - 24, 296 - 24), position.calculatePosition(trigger, IntSize(360, 800), LayoutDirection.Ltr, size))
        assertFalse(above)
        assertEquals(trigger.translate(-88, -272), anchor)
        val low = IntRect(200, 700, 312, 720)
        assertEquals(IntOffset(112 - 24, 724 - 100 - 24), position.calculatePosition(low, IntSize(360, 800), LayoutDirection.Ltr, size))
        assertTrue(above)
    }

    @Test fun compactTextStylesOnlyChangeFontSizes() {
        val defaults = defaultTextStyles()
        val compact = TsukiTextStyles
        val styles = listOf(
            Triple(defaults.title1, compact.title1, 30), Triple(defaults.title2, compact.title2, 22),
            Triple(defaults.title3, compact.title3, 17), Triple(defaults.title4, compact.title4, 17),
            Triple(defaults.headline1, compact.headline1, 16), Triple(defaults.headline2, compact.headline2, 15),
            Triple(defaults.main, compact.main, 16), Triple(defaults.button, compact.button, 16),
            Triple(defaults.paragraph, compact.paragraph, 16), Triple(defaults.body1, compact.body1, 15),
            Triple(defaults.body2, compact.body2, 13), Triple(defaults.subtitle, compact.subtitle, 14),
            Triple(defaults.footnote1, compact.footnote1, 12), Triple(defaults.footnote2, compact.footnote2, 10),
        )
        styles.forEach { (original, actual, size) -> assertEquals(original.copy(fontSize = size.sp), actual) }
    }
}
