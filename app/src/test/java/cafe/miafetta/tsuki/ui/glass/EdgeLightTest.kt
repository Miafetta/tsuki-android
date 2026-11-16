package cafe.miafetta.tsuki.ui.glass

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class EdgeLightTest {
    // The theme store chip's rim by outward normal, screen degrees (0 right, clockwise, 90 down).
    private val table = listOf(0f to .09f, 60f to .16f, 90f to .135f, 150f to .09f, 180f to .09f, 240f to .16f,
        270f to .135f, 330f to .09f, 360f to .09f)
    private val samples = (0 until 3600).map { it / 10f }

    @Test fun capsuleLightMatchesTheMeasuredTable() {
        for ((angle, alpha) in table) assertEquals("at $angle", alpha, capsuleEdgeLightAlpha(angle), 1e-6f)
        // Linear between neighbours, and the straight edges meet the ends without a step.
        assertEquals((.09f + .16f) / 2f, capsuleEdgeLightAlpha(30f), 1e-6f)
        assertEquals((.135f + .09f) / 2f, capsuleEdgeLightAlpha(120f), 1e-6f)
        assertEquals(CapsuleLightStraight, capsuleEdgeLightAlpha(90f), 1e-6f)
        assertEquals(CapsuleLightStraight, capsuleEdgeLightAlpha(270f), 1e-6f)
        assertEquals(capsuleEdgeLightAlpha(0f), capsuleEdgeLightAlpha(360f), 0f)
        assertEquals(capsuleEdgeLightAlpha(60f), capsuleEdgeLightAlpha(-300f), 1e-6f)
        assertEquals(capsuleEdgeLightAlpha(60f), capsuleEdgeLightAlpha(420f), 1e-6f)
    }

    @Test fun capsuleLightIsSymmetricUnderAHalfTurnAndBrightestTopLeftAndBottomRight() {
        for (angle in samples) assertEquals("at $angle", capsuleEdgeLightAlpha(angle), capsuleEdgeLightAlpha(angle + 180f), 1e-6f)
        val brightest = samples.maxOf { capsuleEdgeLightAlpha(it) }
        assertEquals(brightest, capsuleEdgeLightAlpha(60f), 0f)
        assertEquals(brightest, capsuleEdgeLightAlpha(240f), 0f)
        assertEquals(listOf(60f, 240f), samples.filter { capsuleEdgeLightAlpha(it) == brightest })
        // The top-right and bottom-left turns are no brighter than the ends.
        assertTrue(capsuleEdgeLightAlpha(330f) <= capsuleEdgeLightAlpha(0f))
        assertTrue(capsuleEdgeLightAlpha(150f) <= capsuleEdgeLightAlpha(180f))
    }

    @Test fun sweepStopsFollowTheGradientAngleConvention() {
        val stops = capsuleEdgeLightStops()
        assertEquals(table.map { it.first / 360f }, stops.map { it.first })
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(1f, stops.last().first, 0f)
        assertTrue(stops.zipWithNext().all { (a, b) -> b.first > a.first })
        for ((fraction, alpha) in stops) assertEquals(capsuleEdgeLightAlpha(fraction * 360f), alpha, 1e-5f)
        // Evaluate the stops as the sweep shader does, at screen-space normals (x right, y down).
        fun at(x: Double, y: Double) = gradientAlpha(stops, sweepFraction(x, y))
        assertEquals(.09f, at(1.0, 0.0), 1e-6f)
        assertEquals(CapsuleLightStraight, at(0.0, 1.0), 1e-6f)
        assertEquals(.09f, at(-1.0, 0.0), 1e-6f)
        assertEquals(CapsuleLightStraight, at(0.0, -1.0), 1e-6f)
        val bottomRight = at(cos(PI / 3), sin(PI / 3))
        val topLeft = at(-cos(PI / 3), -sin(PI / 3))
        assertEquals(.16f, bottomRight, 1e-5f)
        assertEquals(.16f, topLeft, 1e-5f)
        // Mirrored stops would light the other diagonal: the top-right and bottom-left turns stay dim.
        assertTrue(at(1.0, -1.0) < bottomRight && at(-1.0, 1.0) < topLeft)
        assertTrue(at(1.0, 1.0) > at(1.0, -1.0) && at(-1.0, -1.0) > at(-1.0, 1.0))
    }

    @Test fun onlyTheLightNativePathTakesOffTheMaterialsOwnRim() {
        // Second reading (2026-11-15) with the dock's material: about 16 of 255 levels, within .054-.070.
        assertEquals(.062f, NativeLightChipEdgeLightReduction, 0f)
        assertTrue(NativeLightChipEdgeLightReduction in 0.054f..0.070f)
        assertEquals(NativeLightChipEdgeLightReduction, chipEdgeLightReduction(native = true, dark = false), 0f)
        assertEquals(0f, chipEdgeLightReduction(native = true, dark = true), 0f)
        assertEquals(0f, chipEdgeLightReduction(native = false, dark = false), 0f)
        assertEquals(0f, chipEdgeLightReduction(native = false, dark = true), 0f)
    }

    @Test fun reducedCapsuleLightMatchesItsBreakPoints() {
        val reduction = NativeLightChipEdgeLightReduction
        val reduced = listOf(0f to .028f, 60f to .098f, 90f to .073f, 150f to .028f, 180f to .028f, 240f to .098f,
            270f to .073f, 330f to .028f, 360f to .028f)
        for ((angle, alpha) in reduced) assertEquals("at $angle", alpha, capsuleEdgeLightAlpha(angle, reduction), 1e-6f)
        assertEquals(.073f, capsuleEdgeLightAlpha(Float.NaN, reduction), 1e-6f)
        // No reduction leaves the measured table as it is.
        for (angle in samples) assertEquals("at $angle", capsuleEdgeLightAlpha(angle), capsuleEdgeLightAlpha(angle, 0f), 0f)
        assertEquals(capsuleEdgeLightStops(), capsuleEdgeLightStops(0f))
    }

    @Test fun reducedCapsuleLightKeepsItsDirectionsAndNeverGoesNegative() {
        val reduction = NativeLightChipEdgeLightReduction
        for (angle in samples) {
            val alpha = capsuleEdgeLightAlpha(angle, reduction)
            assertTrue("at $angle", alpha >= 0f)
            assertEquals("at $angle", capsuleEdgeLightAlpha(angle) - reduction, alpha, 1e-6f)
        }
        val brightest = samples.maxOf { capsuleEdgeLightAlpha(it, reduction) }
        assertEquals(listOf(60f, 240f), samples.filter { capsuleEdgeLightAlpha(it, reduction) == brightest })
        assertTrue(capsuleEdgeLightAlpha(90f, reduction) > capsuleEdgeLightAlpha(0f, reduction))
        // A reduction beyond the table clamps at 0 instead of going negative.
        assertTrue(samples.all { capsuleEdgeLightAlpha(it, .2f) == 0f })
        assertTrue(capsuleEdgeLightStops(.2f).all { it.second == 0f })
    }

    @Test fun reducingTheStopsEqualsReducingTheInterpolatedAlpha() {
        // The sweep shader reduces at the break points and then interpolates; capsuleEdgeLightAlpha interpolates and then
        // reduces. With the reduction below the table's smallest alpha (.09) the two agree at every angle.
        val reduction = NativeLightChipEdgeLightReduction
        val stops = capsuleEdgeLightStops(reduction)
        for (angle in samples) {
            assertEquals("at $angle", capsuleEdgeLightAlpha(angle, reduction), gradientAlpha(stops, angle / 360f), 1e-6f)
        }
    }

    // android.graphics.SweepGradient (Brush.sweepGradient on Android) takes its parameter from Skia's xy_to_unit_angle:
    // atan2(y, x) in turns, 0 along +x and a quarter turn along +y.
    private fun sweepFraction(x: Double, y: Double): Float = ((atan2(y, x) / (2 * PI) + 1.0) % 1.0).toFloat()

    private fun gradientAlpha(stops: List<Pair<Float, Float>>, t: Float): Float {
        val end = stops.indexOfFirst { it.first >= t }.coerceAtLeast(1)
        val (from, a) = stops[end - 1]
        val (to, b) = stops[end]
        return a + (b - a) * (t - from) / (to - from)
    }
}
