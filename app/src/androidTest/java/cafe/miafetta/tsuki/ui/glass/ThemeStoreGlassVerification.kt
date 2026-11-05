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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import java.io.File

internal fun themeStoreGlassVerification(runner: Instrumentation, host: ComponentActivity) {
    val folder = File(host.getExternalFilesDir(null), "theme-header").apply { mkdirs() }
    val input = checkNotNull(BitmapFactory.decodeFile(File(folder, "idle.jpg").path))
    check(input.width == 1200 && host.resources.displayMetrics.widthPixels == 1200)
    val banner = Bitmap.createBitmap(input, 0, 546, 1200, 554).asImageBitmap()
    val calibrated = mutableStateOf(false)
    runner.runOnMainSync {
        host.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1200f / 360f, 1f),
                LocalForceMiuixGlass provides calibrated.value) {
                TsukiAppTheme("LIGHT") {
                    TsukiGlassHost(true) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFF3F3F3))) {
                            Canvas(Modifier.fillMaxSize().tsukiBackdrop()) { drawImage(banner, Offset(0f, 244f)) }
                            TsukiGlass({ 1f }, 100.4.dp,
                                Modifier.fillMaxWidth().height(100.4.dp + if (calibrated.value) GameHeaderFade else GameChipGlassFade),
                                gameHeader = calibrated.value)
                        }
                    }
                }
            }
        }
    }
    for (value in listOf(false, true)) {
        runner.runOnMainSync { calibrated.value = value }
        Thread.sleep(2500)
        val bitmap = checkNotNull(runner.uiAutomation.takeScreenshot())
        File(folder, if (value) "calibrated.png" else "native-before.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    val reference = checkNotNull(BitmapFactory.decodeFile(File(folder, "scrolled.jpg").path))
    val before = checkNotNull(BitmapFactory.decodeFile(File(folder, "native-before.png").path))
    val after = checkNotNull(BitmapFactory.decodeFile(File(folder, "calibrated.png").path))
    val oldError = bannerError(before, reference)
    val newError = bannerError(after, reference)
    check(newError < oldError * .5) { "Banner background did not approach the reference: $oldError -> $newError" }
    File(folder, "comparison.txt").writeText(
        "Fallback RGB mean error outside chips: $oldError -> $newError; forced miuix, not default native appearance")
    reference.recycle()
    before.recycle()
    after.recycle()
    input.recycle()
}

private fun bannerError(actual: Bitmap, reference: Bitmap): Double {
    var total = 0L
    var count = 0L
    for (y in 300 until 520) for (x in 60 until 1140) {
        if (y in 346..479 && x !in 332..359 && x !in 658..685) continue
        val a = actual.getPixel(x, y)
        val b = reference.getPixel(x, y)
        for (shift in listOf(16, 8, 0)) {
            total += kotlin.math.abs((a shr shift and 255) - (b shr shift and 255))
            count++
        }
    }
    return total.toDouble() / count
}
