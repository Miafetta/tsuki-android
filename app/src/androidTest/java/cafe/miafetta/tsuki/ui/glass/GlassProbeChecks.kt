package cafe.miafetta.tsuki.ui.glass

import android.graphics.Bitmap
import android.view.View
import java.io.File
import java.util.Locale
import kotlin.math.abs

// The probe's checks (GlassSurfaceVerification.kt). Blur is judged on whole rows of the 8dp stripes: the red channel's spread
// along a row shrinks as the stripes are blurred together, and the mean difference from the unblurred stripes at sourceY
// (same columns, vertical stripes) is 0 exactly where the page shows through sharp. A pair of pixels is no measure: the two
// edges of one stripe keep their antialiasing difference under a light blur.

// Columns clear of the chips, the small title and the button: the left band above the chips' row, the right band beside it.
private fun GlassProbe.leftBand() = (8 * density).toInt() until (100 * density).toInt()
private fun GlassProbe.rightBand() = (210 * density).toInt() until (290 * density).toInt()

private fun red(bitmap: Bitmap, x: Int, y: Int) = bitmap.getPixel(x, y) shr 16 and 255

internal fun GlassProbe.spread(bitmap: Bitmap, y: Int, columns: IntRange = leftBand()): Int {
    val reds = columns.map { red(bitmap, it, y) }
    return reds.max() - reds.min()
}

internal fun GlassProbe.blurShift(bitmap: Bitmap, y: Int, columns: IntRange = leftBand()): Float =
    columns.map { abs(red(bitmap, it, y) - red(bitmap, it, sourceY)) }.average().toFloat()

private fun GlassProbe.blurred(bitmap: Bitmap, y: Int, columns: IntRange = leftBand()) =
    spread(bitmap, y, columns) < spread(bitmap, sourceY, columns) / 2

// The first pixel of rows [from, from + rows) that differs between the two images (or from sourceY when [b] is null).
private fun firstDifference(a: Bitmap, b: Bitmap?, from: Int, rows: Int, sourceY: Int,
    skip: (Int, Int) -> Boolean = { _, _ -> false }): Pair<Int, Int>? =
    (from until from + rows).asSequence().flatMap { y -> (0 until a.width).asSequence().map { x -> x to y } }
        .firstOrNull { (x, y) -> !skip(x, y) && a.getPixel(x, y) != (b ?: a).getPixel(x, if (b == null) sourceY else y) }

// Below the title threshold the background and chips redraw at every step and the native top view is reused.
internal fun GlassProbe.scrollSteps(name: String, idle: Bitmap, idleNative: MiGlassView?) {
    val sampleX = (40 * density).toInt()
    var previousPixel = idle.getPixel(sampleX, (statusTop + 10 * density).toInt())
    var previousChipPixel = idle.getPixel(chipX, chipY)
    for ((index, fraction) in listOf(1f / 12f, 1f / 6f, 1f / 4f).withIndex()) {
        runner.runOnMainSync { checkNotNull(probeState).let { it.heightOffset = it.heightOffsetLimit * fraction } }
        settle()
        expect(backgroundProgress > 0f && backgroundProgress < 1f) { "$name: background still switches at the title threshold" }
        runner.runOnMainSync {
            val top = nativeTop()
            if (idleNative != null) expect(top != null && top === idleNative && top.alpha == 1f && top.visibility == View.VISIBLE) {
                "$name: native top glass recreated or not shown at scroll step $index"
            }
        }
        val step = capture("probe-$name-scroll-$index")
        val pixel = step.getPixel(sampleX, (statusTop + 10 * density).toInt())
        expect(pixel != previousPixel) { "$name: background did not redraw at scroll step $index" }
        val chipPixel = step.getPixel(chipX, chipY)
        expect(chipPixel != previousChipPixel) { "$name: chip did not follow the header at scroll step $index" }
        previousChipPixel = chipPixel
        previousPixel = pixel
        step.recycle()
    }
}

// Just past the 1/3 title threshold the glass is at full strength over unchanged content; returns that capture.
internal fun GlassProbe.titleThreshold(name: String, idle: Bitmap): Bitmap {
    runner.runOnMainSync {
        val state = checkNotNull(probeState)
        check(state.heightOffsetLimit < 0f && state.heightOffsetLimit > -host.window.decorView.height) {
            "TopAppBar was not measured: ${state.heightOffsetLimit}"
        }
        state.heightOffset = state.heightOffsetLimit * (1f / 3f + .001f)
    }
    settle()
    val glass = capture("probe-$name-title-threshold")
    expect(actualProgress == 1f) { "$name: glass not at full strength over stationary content: $actualProgress" }
    val buttonY = statusTop + (26 * density).toInt()
    val buttonX = (host.window.decorView.width - 46 * density).toInt()
    expect(idle.getPixel(buttonX, buttonY) != glass.getPixel(buttonX, buttonY)) { "$name: button backdrop did not redraw" }
    expect(blurred(glass, 0)) { "$name: first pixel row shows unblurred stripes" }
    expect(blurred(glass, statusTop + (8 * density).toInt())) { "$name: top bar background does not blur the stripes" }
    val chipBand = chipX - (20 * density).toInt() until chipX + (20 * density).toInt()
    expect(blurred(glass, chipY, chipBand)) { "$name: unselected chip glass does not blur the stripes" }
    // No geometry or source movement: below the game row (its clear line is 2dp above it) both images keep the same pixels,
    // the selected chip's shadow included.
    val bottomY = statusTop + ((52 + GameChipSlot.value + 2) * density).toInt()
    firstDifference(idle, glass, bottomY, 40, sourceY)?.let { (x, y) -> expect(false) { "$name: blur leaked below its fade at $x,$y" } }
    return glass
}

// The forced Miuix fallback still blurs; with blur off the chip matches idle. Returns the blur-off capture.
internal fun GlassProbe.fallbacks(name: String, idle: Bitmap, glass: Bitmap): Bitmap {
    runner.runOnMainSync { forceMiuix.value = true }
    settle()
    runner.runOnMainSync { expect(nativeViews().isEmpty()) { "$name: forced fallback still created native views" } }
    val forced = capture("probe-$name-forced-miuix")
    expect(blurred(forced, statusTop + (8 * density).toInt())) { "$name: fallback lost the backdrop after native consumers left" }
    expect(blurred(forced, 0)) { "$name: fallback shows an unblurred first row" }
    forced.recycle()
    runner.runOnMainSync {
        forceMiuix.value = false
        enabled.value = false
    }
    settle()
    val fallback = capture("probe-$name-blur-off")
    expect(idle.getPixel(chipX, chipY) == fallback.getPixel(chipX, chipY)) { "$name: fallback chip does not match its idle colour" }
    expect(glass.getPixel(chipX, chipY) != fallback.getPixel(chipX, chipY)) { "$name: glass chip did not sample the background" }
    return fallback
}

// Other pages. MiGlass: full strength down to 16dp above the action row, then a cross-fade (blurred x alpha + sharp x
// (1 - alpha)) to the clear line 8dp below it. miuix-blur: a progressive blur whose clear end keeps a few px of sigma,
// faded out over the last 8dp. Either way the stripes are still partly blurred at the action row and sharp from 8dp below.
internal fun GlassProbe.standardFade(name: String) {
    runner.runOnMainSync {
        overview.value = false
        enabled.value = true
    }
    settle()
    val standard = capture("probe-$name-standard-fade")
    val title = statusTop + (30 * density).toInt()
    writeFadeProfile("$name-other", standard, actionRow + (12 * density).toInt(), leftBand())
    expect(blurred(standard, 0)) { "$name: non-home top pixel row is unblurred" }
    expect(blurShift(standard, actionRow) >= .3f) {
        "$name: non-home fade already clear at the action row (shift ${blurShift(standard, actionRow)})"
    }
    expect(blurShift(standard, title) >= blurShift(standard, actionRow)) {
        "$name: non-home blur weaker in the title row (${blurShift(standard, title)}) than at the action row " +
            "(${blurShift(standard, actionRow)})"
    }
    val fadeEnd = actionRow + ((TopGlassFade.value + 2) * density).toInt()
    firstDifference(standard, null, fadeEnd, 40, sourceY)?.let { (x, y) ->
        expect(false) { "$name: non-home blur leaked below its clear line at $x,$y" }
    }
    runner.runOnMainSync { enabled.value = false }
    settle()
    capture("probe-$name-standard-fallback").recycle()
    standard.recycle()
}

// The home header. MiGlass: full strength down to the action row, then the cross-fade to 8dp below the chips (50dp);
// miuix-blur: the progressive blur over the same height. Half way down the chips the stripes beside them are partly blurred.
internal fun GlassProbe.gameHeaderFade(name: String) {
    runner.runOnMainSync {
        overview.value = true
        enabled.value = true
    }
    settle()
    runner.runOnMainSync {
        if (MiGlassCapability.supported) expect(nativeTop()?.visibility == View.VISIBLE) { "$name: game header not on native glass" }
    }
    val game = capture("probe-$name-game-header")
    writeFadeProfile("$name-home", game, actionRow + ((GameHeaderFade.value + 4) * density).toInt(), rightBand(), actionRow)
    expect(blurred(game, 0)) { "$name: game header exposes an unblurred first row" }
    val middle = actionRow + (25 * density).toInt()
    expect(blurShift(game, middle, rightBand()) >= .3f) { "$name: game header already clear half way down the chips" }
    val gameEnd = actionRow + ((GameHeaderFade.value + 2) * density).toInt()
    // The selected chip's own shadow is not glass: its bitmap, ChipShadowRadius around the chip, is left out.
    val shadow = chipShadowBox(chipBounds("selected"))
    firstDifference(game, null, gameEnd, 40, sourceY) { x, y -> shadow.contains(x, y) }?.let { (x, y) ->
        expect(false) { "$name: game header leaked below its fade at $x,$y" }
    }
    game.recycle()
}

// The blur shift every 2dp from the status bar down to [bottom], for calibrating the fade on the device; rows from
// [bandFrom] use [columns], rows above it the left band.
private fun GlassProbe.writeFadeProfile(name: String, bitmap: Bitmap, bottom: Int, columns: IntRange, bandFrom: Int = 0) {
    val step = (2 * density).toInt().coerceAtLeast(1)
    val rows = (statusTop until bottom step step).joinToString("\n") { y ->
        val band = if (y >= bandFrom) columns else leftBand()
        "row $y (${"%+.1f".format(Locale.ROOT, (y - actionRow) / density)}dp from the action row): shift " +
            "${"%.2f".format(Locale.ROOT, blurShift(bitmap, y, band))}, spread ${spread(bitmap, y, band)}"
    }
    File(folder, "fade-profile-$name.txt").writeText("sharp spread ${spread(bitmap, sourceY)}\n$rows\n")
}
