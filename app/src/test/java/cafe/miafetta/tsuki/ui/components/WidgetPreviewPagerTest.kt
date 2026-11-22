package cafe.miafetta.tsuki.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPreviewPagerTest {
    @Test fun catalogIndexMatchesGameAndPalette() {
        assertEquals(0, widgetCatalogIndex("arknights", "LIGHT"))
        assertEquals(1, widgetCatalogIndex("arknights", "DARK"))
        assertEquals(2, widgetCatalogIndex("endfield", "DARK"))
        assertEquals(2, widgetCatalogIndex("endfield", "LIGHT"))
        assertEquals(0, widgetCatalogIndex("unknown", "LIGHT"))
    }

    @Test fun incomingCardTiltsSymmetricallyWhileOutgoingStaysFlat() {
        assertEquals(0f, pagerPageOffset(1, 1, 0f), .0001f)
        assertEquals(.7f, pagerPageOffset(1, 0, .3f), .0001f)
        assertEquals(-.7f, pagerPageOffset(0, 1, -.3f), .0001f)

        assertEquals(0f, incomingPageTilt(0, 0, -.3f), .0001f)
        assertEquals(0f, incomingPageTilt(1, 1, 0f), .0001f)
        assertEquals(-WidgetPagerMotion.TiltMaxDegrees, incomingPageTilt(1, 0, 1f), .0001f)
        assertEquals(WidgetPagerMotion.TiltMaxDegrees, incomingPageTilt(0, 1, -1f), .0001f)
        assertEquals(-WidgetPagerMotion.TiltMaxDegrees * .4f, incomingPageTilt(1, 0, .4f), .001f)
        assertEquals(-WidgetPagerMotion.TiltMaxDegrees, incomingPageTilt(2, 0, 1.8f), .0001f)
    }

    @Test fun headerAndSettingsFadeOnlyWhenCrossingGames() {
        assertEquals(1f, crossGameAlpha(0, .4f), .0001f)
        assertEquals(1f, crossGameAlpha(1, -.4f), .0001f)
        assertEquals(1f, crossGameAlpha(0, -.3f), .0001f)
        assertEquals(1f, crossGameAlpha(2, .3f), .0001f)

        assertEquals(.4f, crossGameAlpha(1, .3f), .0001f)
        assertEquals(0f, crossGameAlpha(1, .5f), .0001f)
        assertEquals(.2f, crossGameAlpha(2, -.4f), .0001f)
        assertEquals(1f, crossGameAlpha(2, 0f), .0001f)

        assertEquals(1f, pageCardAlpha(0f), .0001f)
        assertEquals(.75f, pageCardAlpha(.5f), .0001f)
        assertEquals(.75f, pageCardAlpha(-.5f), .0001f)
        assertEquals(.5f, pageCardAlpha(1f), .0001f)
        assertEquals(.5f, pageCardAlpha(-1.5f), .0001f)

        assertEquals(1f, pageSettingsAlpha(0, 0f, 3), .0001f)
        assertEquals(.4f, pageSettingsAlpha(0, .3f, 3), .0001f)
        assertEquals(0f, pageSettingsAlpha(0, .5f, 3), .0001f)
        assertEquals(0f, pageSettingsAlpha(1, -.5f, 3), .0001f)
        assertEquals(.4f, pageSettingsAlpha(1, .3f, 3), .0001f)
        assertEquals(1f, pageSettingsAlpha(0, -.3f, 3), .0001f)
        assertEquals(1f, pageSettingsAlpha(2, .3f, 3), .0001f)
    }

    @Test fun wormIndicatorStretchesAcrossDotsAroundMidpoint() {
        assertEquals(0f to 0f, wormSpan(0f, 3))
        assertEquals(0f to .5f, wormSpan(.25f, 3))
        assertEquals(0f to 1f, wormSpan(.5f, 3))
        assertEquals(.5f to 1f, wormSpan(.75f, 3))
        assertEquals(1f to 1f, wormSpan(1f, 3))
        assertEquals(2f to 2f, wormSpan(2f, 3))
        assertEquals(0f to 0f, wormSpan(-0.4f, 3))
        assertEquals(2f to 2f, wormSpan(2.8f, 3))
    }
}
