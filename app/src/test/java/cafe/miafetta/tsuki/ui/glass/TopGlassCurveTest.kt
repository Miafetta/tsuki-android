package cafe.miafetta.tsuki.ui.glass

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults

class TopGlassCurveTest {
    private val density = 3.25f
    private fun px(dp: Float) = dp * density
    private fun curve(height: Float, status: Float) =
        topGlassCurve(height, status, px(TopGlassPlateau.value), px(TopGlassClearFade.value))

    @Test fun homeHeaderIsStrongestBelowTheStatusBarAndFadesBelowTheChips() {
        val status = px(40f)
        val collapsed = status + px(TopAppBarDefaults.CollapsedHeight.value)
        val height = collapsed + px(GameHeaderFade.value)
        val result = curve(height, status)
        assertEquals((status + px(6f)) / height, result.fullUntil, 1e-6f)
        // The sampled result fades out from the chip bottom to the clear line 8dp below it.
        assertEquals(collapsed + px(GameChipBottom.value), result.fadeFrom * height, 1e-3f)
        assertTrue(result.fullUntil < result.fadeFrom)
    }

    @Test fun otherPagesShareTheMaterialAndClearBelowTheActionRow() {
        val status = px(40f)
        val collapsed = status + px(TopAppBarDefaults.CollapsedHeight.value)
        val home = collapsed + px(GameHeaderFade.value)
        val other = collapsed + px(TopGlassFade.value)
        // Full strength ends at the same row on both; only the clear line moves.
        assertEquals(curve(home, status).fullUntil * home, curve(other, status).fullUntil * other, 1e-3f)
        assertEquals(collapsed, curve(other, status).fadeFrom * other, 1e-3f)
    }

    @Test fun boundariesFollowTheStatusBarOnEveryDevice() {
        for (statusDp in listOf(0f, 24f, 32.5f, 40f, 48f, 64f)) {
            val status = px(statusDp)
            val height = status + px(52f + GameHeaderFade.value)
            val result = curve(height, status)
            assertEquals(status + px(TopGlassPlateau.value), result.fullUntil * height, 1e-3f)
            assertEquals(height - px(TopGlassClearFade.value), result.fadeFrom * height, 1e-3f)
            assertTrue(result.fullUntil in 0f..1f && result.fadeFrom in 0f..1f && result.fullUntil < result.fadeFrom)
        }
    }

    @Test fun unmeasuredOrInvalidInputsGiveAValidCurve() {
        assertEquals(TopGlassCurve(0f, 0f), curve(0f, px(40f)))
        assertEquals(TopGlassCurve(0f, 0f), curve(-12f, px(40f)))
        assertEquals(TopGlassCurve(0f, 0f), curve(Float.NaN, px(40f)))
        assertEquals(TopGlassCurve(0f, 0f), curve(Float.POSITIVE_INFINITY, px(40f)))
        // A missing status bar counts as none; full strength still reaches the plateau.
        assertEquals(px(6f) / 300f, curve(300f, Float.NaN).fullUntil, 1e-6f)
        assertEquals(px(6f) / 300f, curve(300f, -20f).fullUntil, 1e-6f)
        assertEquals(1f, topGlassCurve(300f, 0f, Float.NaN, Float.NaN).fadeFrom, 0f)
        assertEquals(0f, topGlassCurve(300f, 0f, Float.NaN, Float.NaN).fullUntil, 0f)
    }

    @Test fun fractionsAreClampedToTheGlass() {
        // A status bar taller than the glass: full strength everywhere, fade still at the bottom.
        val tall = curve(px(30f), px(40f))
        assertEquals(1f, tall.fullUntil, 0f)
        // A fade longer than the glass starts at the top.
        assertEquals(0f, topGlassCurve(10f, 0f, 0f, 40f).fadeFrom, 0f)
    }

    @Test fun nativeTopGlassFadesFromTheActionRowAtHomeAnd16dpAboveItElsewhere() {
        val status = px(40f)
        val actionRow = status + px(TopAppBarDefaults.CollapsedHeight.value)
        val home = actionRow + px(GameHeaderFade.value)
        val other = actionRow + px(TopGlassFade.value)
        val above = px(NativeTopGlassFadeAbove.value)
        assertEquals(actionRow / home, nativeTopGlassFadeFrom(home, actionRow, 0f), 1e-6f)
        assertEquals((actionRow - px(16f)) / other, nativeTopGlassFadeFrom(other, actionRow, above), 1e-6f)
        // The fades span 50dp (to 8dp below the chips) and 24dp (to 8dp below the action row).
        assertEquals(px(50f), home * (1f - nativeTopGlassFadeFrom(home, actionRow, 0f)), 1e-3f)
        assertEquals(px(24f), other * (1f - nativeTopGlassFadeFrom(other, actionRow, above)), 1e-3f)
    }

    @Test fun nativeTopGlassFadeIsValidForAnyInput() {
        assertEquals(0f, nativeTopGlassFadeFrom(0f, 300f, 0f), 0f)
        assertEquals(0f, nativeTopGlassFadeFrom(Float.NaN, 300f, 0f), 0f)
        assertEquals(0f, nativeTopGlassFadeFrom(Float.POSITIVE_INFINITY, 300f, 0f), 0f)
        assertEquals(1f, nativeTopGlassFadeFrom(100f, 300f, 0f), 0f)
        assertEquals(0f, nativeTopGlassFadeFrom(300f, 10f, 40f), 0f)
        assertEquals(0f, nativeTopGlassFadeFrom(300f, Float.NaN, 0f), 0f)
        assertEquals(.5f, nativeTopGlassFadeFrom(300f, 150f, Float.NaN), 0f)
    }

    @Test fun topGlassRadiiFollowTheDeviceDecision() {
        // MiGlass first at 14; the miuix-blur fallback lowered to 10dp.
        assertEquals(14f, NativeTopGlassRadius, 0f)
        assertEquals(16.dp, NativeTopGlassFadeAbove)
        assertEquals(10.dp, TopGlassBlurRadius)
    }

    @Test fun nativeTopGlassSigmaMatchesTheThemeStore() {
        // Device calibration (2026-11-16): radius 17 = 78 MiGlass px blurred with sigma 37 px, about 0.475 x those pixels.
        val sigmaPerPixel = 37f / miGlassRadiusPixels(17f)
        assertEquals(78, miGlassRadiusPixels(17f))
        assertEquals(.475f, sigmaPerPixel, .005f)
        // At 14 (64 px) the predicted sigma falls in the theme store's strongest 28-31 px.
        assertEquals(64, miGlassRadiusPixels(NativeTopGlassRadius))
        assertTrue(sigmaPerPixel * miGlassRadiusPixels(NativeTopGlassRadius) in 28f..31f)
    }

    @Test fun parametersStayWithinTheMeasuredProfile() {
        // The system's full strength ends 6-9dp below the status bar; the fade begins at the chip or action row bottom.
        assertTrue(TopGlassPlateau in 6.dp..9.dp)
        assertEquals(TopGlassFade, TopGlassClearFade)
        assertTrue(TopGlassBlurCurve > 0f && TopGlassBlurCurve.isFinite())
        assertTrue(TopGlassBlurRadius > 0.dp)
    }
}
