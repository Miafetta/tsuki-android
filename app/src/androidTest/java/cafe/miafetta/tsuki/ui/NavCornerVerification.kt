package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme

import android.graphics.Bitmap
import android.graphics.Color
import android.view.RoundedCorner
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import java.io.File

// Slides the add-account page in and out over a saturated backdrop and samples screenshots mid-slide: the entering page's
// leading corners must show the backdrop (rounded like a system window transition), its leading edge must not.
internal fun navCornerVerification(runner: WidgetRenderInstrumentation, host: MainActivity): String {
    val show = mutableStateOf(false)
    val folder = File(runner.targetContext.getExternalFilesDir(null), "nav-corner").apply { mkdirs() }
    runner.runOnMainSync {
        host.setContent {
            TsukiAppTheme("LIGHT") {
                AccountNavigationFixture(host, show.value, { show.value = false }) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(BACKDROP)))
                }
            }
        }
    }
    runner.settleUi()
    var screenRadius = 0
    runner.runOnMainSync {
        screenRadius = host.window.decorView.rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
    }
    check(screenRadius > 0) { "Device reports no screen corner radius" }
    // Before the window attaches (production composes in onCreate) Miuix falls back to this framework dimen.
    @android.annotation.SuppressLint("DiscouragedApi")
    val dimen = host.resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
    val fallback = if (dimen > 0) host.resources.getDimensionPixelSize(dimen) else 0
    val recording = runner.uiAutomation.executeShellCommand(
        "screenrecord --bit-rate 16000000 --time-limit 5 ${folder.absolutePath}/slide.mp4")
    try {
        Thread.sleep(600)
        runner.runOnMainSync { show.value = true }
        val opening = sampleSlide(runner, folder, "opening")
        runner.settleUi()
        runner.runOnMainSync { show.value = false }
        val closing = sampleSlide(runner, folder, "closing")
        runner.settleUi()
        return "PASS: screen corner ${screenRadius}px (pre-attach fallback ${fallback}px); opening $opening; closing $closing"
    } finally {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() }
    }
}

private const val BACKDROP = 0xFF1E64FF

// Takes screenshots until one shows the page edge between 15% and 85% of the width, then checks that frame.
private fun sampleSlide(runner: WidgetRenderInstrumentation, folder: File, phase: String): String {
    val deadline = System.currentTimeMillis() + 2000
    while (System.currentTimeMillis() < deadline) {
        val frame = checkNotNull(runner.uiAutomation.takeScreenshot())
        val edge = pageEdge(frame)
        if (edge > frame.width * 15 / 100 && edge < frame.width * 85 / 100) {
            File(folder, "$phase.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = checkCorners(frame, edge, phase)
            frame.recycle()
            return result
        }
        frame.recycle()
    }
    error("No mid-slide frame captured while $phase")
}

// First column from the left on the middle row that belongs to the light page rather than the dimmed backdrop.
private fun pageEdge(frame: Bitmap): Int {
    val y = frame.height / 2
    return (0 until frame.width).firstOrNull { !isBackdrop(frame.getPixel(it, y)) } ?: -1
}

private fun isBackdrop(pixel: Int) = Color.blue(pixel) - Color.red(pixel) > 40

private fun checkCorners(frame: Bitmap, edge: Int, phase: String): String {
    val inset = 4
    val top = frame.getPixel(edge + inset, inset)
    val bottom = frame.getPixel(edge + inset, frame.height - 1 - inset)
    val middle = frame.getPixel(edge + inset, frame.height / 2)
    check(isBackdrop(top) && isBackdrop(bottom)) {
        "Square leading corners while $phase at x=$edge: top=${Integer.toHexString(top)} bottom=${Integer.toHexString(bottom)}"
    }
    check(!isBackdrop(middle)) { "Leading edge is not the page while $phase at x=$edge" }
    // Horizontal extent of the top-left rounding on the first row: how far the backdrop reaches into the page.
    val extent = (edge until frame.width).firstOrNull { !isBackdrop(frame.getPixel(it, 0)) }?.minus(edge) ?: -1
    return "edge x=$edge, top-row rounding ${extent}px"
}
