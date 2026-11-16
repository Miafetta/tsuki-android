// Precondition of the theme-header scenario: the maintainer's own theme store screenshots must already be on the phone, in
// this app's external files folder as theme-header/idle.jpg and theme-header/scrolled.jpg. They are never pushed by the
// tooling and not in the repository; without them the scenario cannot run (decodeFile returns null) and is skipped.
package cafe.miafetta.tsuki.ui.glass

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import java.io.File
import java.util.Locale

// The theme store home page on the 1200 px wide test phone (3.25 px/dp, recorded 2026-11-14): status bar bottom, and the
// row from which the page is sharp again, 8dp below the chip row. The banner (idle.jpg from row 546) is drawn where the
// theme store has scrolled it to in scrolled.jpg.
private const val StoreStatusBar = 130
private const val StoreClearLine = 480
private const val BannerTop = 244f

// Lays Tsuki's home page header over the theme store banner, in the theme store's geometry and at the display's real
// density, so it can be compared with the theme store's own glass while the blur is calibrated on the device. The scenario
// folder holds idle.jpg (page at the top, banner unobstructed) and scrolled.jpg (banner under the theme store's header).
internal fun themeStoreGlassVerification(runner: Instrumentation, host: ComponentActivity) {
    val folder = File(host.getExternalFilesDir(null), "theme-header").apply { mkdirs() }
    val input = checkNotNull(BitmapFactory.decodeFile(File(folder, "idle.jpg").path))
    check(input.width == 1200 && host.resources.displayMetrics.widthPixels == 1200)
    val banner = Bitmap.createBitmap(input, 0, 546, 1200, 554).asImageBitmap()
    val progress = mutableFloatStateOf(1f)
    runner.runOnMainSync {
        host.setContent {
            val density = LocalDensity.current
            TsukiAppTheme("LIGHT") {
                TsukiGlassHost(true) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFF3F3F3))) {
                        Canvas(Modifier.fillMaxSize().tsukiBackdrop()) { drawImage(banner, Offset(0f, BannerTop)) }
                        TsukiGlass({ progress.floatValue }, with(density) { StoreStatusBar.toDp() },
                            Modifier.fillMaxWidth().height(with(density) { StoreClearLine.toDp() }), gameHeader = true)
                    }
                }
            }
        }
    }
    // Full progress shows the header; zero leaves the bare banner as the unblurred baseline.
    for ((value, name) in listOf(1f to "tsuki-header.png", 0f to "bare-banner.png")) {
        runner.runOnMainSync { progress.floatValue = value }
        Thread.sleep(2500)
        val bitmap = checkNotNull(runner.uiAutomation.takeScreenshot())
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    val reference = checkNotNull(BitmapFactory.decodeFile(File(folder, "scrolled.jpg").path))
    val header = checkNotNull(BitmapFactory.decodeFile(File(folder, "tsuki-header.png").path))
    val bare = checkNotNull(BitmapFactory.decodeFile(File(folder, "bare-banner.png").path))
    val headerError = bannerError(header, reference, 300 until 520)
    val bareError = bannerError(bare, reference, 300 until 520)
    check(headerError < bareError) { "Header did not move the banner towards the theme store: $bareError -> $headerError" }
    val bands = (300 until 520 step 20).joinToString("\n") { top ->
        val rows = top until top + 20
        String.format(Locale.ROOT, "  rows %d-%d: header %.2f, bare %.2f", top, top + 20,
            bannerError(header, reference, rows), bannerError(bare, reference, rows))
    }
    File(folder, "comparison.txt").writeText("RGB mean error against the theme store outside the chips, rows 300-520: " +
        "bare banner $bareError, Tsuki header $headerError (status bar $StoreStatusBar px, clear from $StoreClearLine px, " +
        "MiGlass radius $NativeTopGlassRadius where supported, else miuix-blur $TopGlassBlurRadius, curve $TopGlassBlurCurve)\n" +
        "$bands\n")
    reference.recycle()
    header.recycle()
    bare.recycle()
    input.recycle()
}

private fun bannerError(actual: Bitmap, reference: Bitmap, rows: IntRange): Double {
    var total = 0L
    var count = 0L
    for (y in rows) for (x in 60 until 1140) {
        // The theme store's chips cover these rows; only the gaps between them show the banner.
        if (y in 346..479 && x !in 332..359 && x !in 658..685) continue
        val a = actual.getPixel(x, y)
        val b = reference.getPixel(x, y)
        for (shift in listOf(16, 8, 0)) {
            total += kotlin.math.abs((a shr shift and 255) - (b shr shift and 255))
            count++
        }
    }
    return if (count > 0) total.toDouble() / count else 0.0
}
