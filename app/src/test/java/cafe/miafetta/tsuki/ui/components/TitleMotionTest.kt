package cafe.miafetta.tsuki.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults

class TitleMotionTest {
    private val eps = .00001f

    @Test fun largeTitleFadesLinearlyOverTheFittedDistance() {
        assertEquals(1f, largeTitleAlpha(0f), 0f)
        assertEquals(1f - 5f / 17.2f, largeTitleAlpha(5f), eps)
        assertEquals(.5f, largeTitleAlpha(8.6f), eps)
        assertEquals(0f, largeTitleAlpha(17.2f), 0f)
        assertEquals(0f, largeTitleAlpha(40f), 0f)
        assertEquals(0f, largeTitleAlpha(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, largeTitleAlpha(-3f), 0f)
        assertEquals(1f, largeTitleAlpha(Float.NaN), 0f)
        val samples = (0..400).map { largeTitleAlpha(it / 20f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b <= a && a - b < .003f })
    }

    @Test fun largeTitleBlurFollowsTheFittedBreakpoints() {
        for ((distance, sigma) in listOf(0f to 0f, 1.5f to 0f, 5f to .46f, 8.6f to 1.54f, 12.6f to 2.15f, 17.2f to 2.3f))
            assertEquals("s=$distance", sigma, largeTitleBlurSigma(distance), eps)
        assertEquals((.46f + 1.54f) / 2f, largeTitleBlurSigma((5f + 8.6f) / 2f), eps)
        assertEquals(2.3f, largeTitleBlurSigma(30f), 0f)
        assertEquals(2.3f, largeTitleBlurSigma(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, largeTitleBlurSigma(-1f), 0f)
        assertEquals(0f, largeTitleBlurSigma(Float.NaN), 0f)
        val samples = (0..400).map { largeTitleBlurSigma(it / 20f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a && b - a < .02f })
        assertTrue(samples.all { it in 0f..2.3f })
    }

    @Test fun smallTitleShowsPastTheThresholdWithoutHysteresis() {
        assertFalse(smallTitleVisible(0f))
        assertFalse(smallTitleVisible(17.2f))
        assertFalse(smallTitleVisible(19.49f))
        assertTrue(smallTitleVisible(19.5f))
        assertTrue(smallTitleVisible(60f))
        assertTrue(smallTitleVisible(Float.POSITIVE_INFINITY))
        assertFalse(smallTitleVisible(-30f))
        assertFalse(smallTitleVisible(Float.NaN))
        // Neither title shows between the large title's fade and the small title's threshold.
        assertTrue(TitleMotion.SmallTitleThresholdDp > TitleMotion.LargeTitleFadeDp)
        assertEquals(0f, largeTitleAlpha(TitleMotion.SmallTitleThresholdDp - .01f), 0f)
    }

    @Test fun smallTitleRisesAndSharpensWithItsProgress() {
        assertEquals(5.5f, smallTitleShift(0f), eps)
        assertEquals(2.75f, smallTitleShift(.5f), eps)
        assertEquals(0f, smallTitleShift(1f), 0f)
        assertEquals(5.5f, smallTitleShift(-1f), eps)
        assertEquals(0f, smallTitleShift(2f), 0f)
        assertEquals(5.5f, smallTitleShift(Float.NaN), eps)
        for ((p, sigma) in listOf(0f to 1.85f, .4f to 1.85f, .55f to 1.4f, .65f to .8f, .76f to .31f, .9f to .15f, 1f to 0f))
            assertEquals("p=$p", sigma, smallTitleBlurSigma(p), eps)
        assertEquals((1.4f + .8f) / 2f, smallTitleBlurSigma(.6f), eps)
        assertEquals(1.85f, smallTitleBlurSigma(-1f), 0f)
        assertEquals(0f, smallTitleBlurSigma(2f), 0f)
        assertEquals(1.85f, smallTitleBlurSigma(Float.NaN), 0f)
        assertEquals(0f, smallTitleAlpha(0f), 0f)
        assertEquals(.3f, smallTitleAlpha(.3f), 0f)
        assertEquals(1f, smallTitleAlpha(1.2f), 0f)
        assertEquals(0f, smallTitleAlpha(Float.NaN), 0f)
        val samples = (0..200).map { it / 200f }
        assertTrue(samples.zipWithNext().all { (a, b) ->
            smallTitleShift(b) <= smallTitleShift(a) && smallTitleBlurSigma(b) <= smallTitleBlurSigma(a) &&
                smallTitleAlpha(b) >= smallTitleAlpha(a)
        })
    }

    @Test fun blurRadiusInvertsTheAospSigmaConversion() {
        // AOSP Blur::convertRadiusToSigma: sigma = 0.57735 * radius + 0.5 for radius > 0.
        for (radius in listOf(.2f, 1f, 4f, 13.5f)) assertEquals(radius, blurRadiusForSigma(.57735f * radius + .5f), .0001f)
        assertEquals((3.25f * 2.3f - .5f) / .57735f, blurRadiusForSigma(3.25f * 2.3f), .0001f)
        assertEquals(0f, blurRadiusForSigma(.5f), 0f)
        assertEquals(0f, blurRadiusForSigma(.2f), 0f)
        assertEquals(0f, blurRadiusForSigma(0f), 0f)
        assertEquals(0f, blurRadiusForSigma(-2f), 0f)
        assertEquals(0f, blurRadiusForSigma(Float.NaN), 0f)
        val samples = (0..300).map { blurRadiusForSigma(it / 20f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun restingTitlesCarryNoBlur() {
        val density = 3.25f
        fun largeRadius(distance: Float) = titleBlurRadius(largeTitleBlurSigma(distance) * density, largeTitleAlpha(distance))
        fun smallRadius(p: Float) = titleBlurRadius(smallTitleBlurSigma(p) * density, smallTitleAlpha(p))
        // Expanded: sharp large title, hidden small title.
        assertEquals(0f, largeTitleBlurSigma(0f), 0f)
        assertEquals(0f, largeRadius(0f), 0f)
        assertEquals(0f, smallRadius(0f), 0f)
        // Fully collapsed: invisible large title, settled small title.
        for (limit in listOf(17.2f, 37f, 55f)) assertEquals(0f, largeRadius(limit), 0f)
        assertEquals(0f, smallTitleBlurSigma(1f), 0f)
        assertEquals(0f, smallTitleShift(1f), 0f)
        assertEquals(0f, smallRadius(1f), 0f)
        // In between, the transition does blur, and never past its fitted maxima.
        assertTrue(largeRadius(8.6f) > 0f && smallRadius(.5f) > 0f)
        assertEquals(0f, titleBlurRadius(10f, 0f), 0f)
        assertEquals(0f, titleBlurRadius(.5f + .57735f * .05f, 1f), 0f)
        assertTrue(titleBlurRadius(.5f + .57735f * .2f, 1f) > 0f)
    }

    @Test fun largeTitleDropsByAFixedOffsetThatCollapsingKeeps() {
        // HyperOS 4 Settings, 2026-11-15: the 30sp ink centred on the system's needs 26.75 px at 3.25 px/dp.
        assertEquals(26.75f / 3.25f, TitleMotion.LargeTitleDropDp, .05f)
        assertEquals(27, Math.round(TitleMotion.LargeTitleDropDp * 3.25f))
        // At 3.25 px/dp: collapsed height 169 px, a drop of 27 px, a 7dp bleed of 23 px.
        val rest = largeTitleTop(169, 27, 23, 0f)
        assertEquals(169 + 27 - 23, rest)
        assertEquals(largeTitleTop(169, 0, 23, 0f) + 27, rest)
        for (offset in listOf(-.4f, -1f, -17.4f, -55.9f, -137f)) {
            assertEquals("offset $offset", rest + Math.round(offset), largeTitleTop(169, 27, 23, offset))
            assertEquals("offset $offset", largeTitleTop(169, 0, 23, offset) + 27, largeTitleTop(169, 27, 23, offset))
        }
        assertEquals(rest, largeTitleTop(169, 27, 23, Float.NaN))
    }

    @Test fun contentAndBackButtonSitWhereTheSystemPutsThem() {
        // HyperOS 4 Settings, 2026-11-16, 3.25 px/dp: the expanded bar ended at row 450 (status bar included), the system's
        // first content starts at row 509.
        val density = 3.25f
        assertEquals(18.dp, TopBarContentGap)
        assertEquals(509f, topBarContentTop((450f / density).dp).value * density, 1f)
        assertEquals(topBarContentTop(100.dp) - 100.dp, TopBarContentGap)
        // The back button ink moves from columns 99..134 onto the system's 86..120; the actions stay where they were.
        val shift = (TopAppBarDefaults.NavigationIconPadding - BackButtonPadding).value * density
        assertEquals(86f, 99f - shift, 1f)
        assertEquals(120f, 134f - shift, 1f)
        assertEquals(16.dp, TopAppBarDefaults.ActionIconPadding)
        // The 40dp button keeps Compose's 48dp minimum touch target inside the bar, which starts at the screen edge.
        assertEquals(40.dp, IconButtonDefaults.MinWidth)
        assertTrue(BackButtonPadding - (48.dp - IconButtonDefaults.MinWidth) / 2 >= 0.dp)
    }

    @Test fun blurMarginHoldsThreeSigmaOfEitherTitle() {
        val margin = titleBlurMarginDp()
        assertEquals(7f, margin, 0f)
        val sigmas = TitleMotion.LargeTitleBlurSigma + TitleMotion.SmallTitleBlurSigma
        assertTrue(sigmas.all { margin >= TitleMotion.BlurSpread * it })
        assertEquals(2.3f, TitleMotion.LargeTitleBlurSigma.max(), 0f)
        assertEquals(1.85f, TitleMotion.SmallTitleBlurSigma.max(), 0f)
        assertTrue(TitleMotion.SmallTitleHideResponse < TitleMotion.SmallTitleShowResponse)
    }
}
