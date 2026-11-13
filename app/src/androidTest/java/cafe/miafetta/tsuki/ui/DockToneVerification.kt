package cafe.miafetta.tsuki.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.components.bottomBarInset
import cafe.miafetta.tsuki.ui.glass.LocalForceMiuixGlass
import cafe.miafetta.tsuki.ui.glass.TsukiGlassHost
import cafe.miafetta.tsuki.ui.glass.tsukiBackdrop
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import java.io.File

// The dock follows the HyperOS 4 Phone app dock. Like the system dock, the capsule measured no darker than a flat light page on
// every backend (2026-11-14), so all three keep the same bound. Its shadow stays outside it.
private const val LIGHT_MAX_DARKER = 3

internal fun dockToneVerification(runner: WidgetRenderInstrumentation, host: MainActivity): String {
    val folder = File(runner.targetContext.getExternalFilesDir(null), "dock-tone").apply { mkdirs() }
    val background = mutableStateOf(Color(0xFFFDFDFD))
    val mode = mutableStateOf("LIGHT")
    val blur = mutableStateOf(true)
    val forceMiuix = mutableStateOf(false)
    runner.runOnMainSync {
        host.setContent {
            CompositionLocalProvider(LocalForceMiuixGlass provides forceMiuix.value) {
                TsukiAppTheme(mode.value) {
                    TsukiGlassHost(blur.value) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(background.value).tsukiBackdrop())
                            TsukiNavDock(0, {},Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBarInset() + 2.dp))
                        }
                    }
                }
            }
        }
    }
    val rows = mutableListOf<String>()
    val problems = mutableListOf<String>()
    for ((name, color) in listOf("white" to Color(0xFFFDFDFD), "gray" to Color(0xFFF2F2F2), "black" to Color.Black)) {
        for (backend in listOf("native", "miuix", "opaque")) {
            runner.runOnMainSync {
                background.value = color
                mode.value = if (name == "black") "DARK" else "LIGHT"
                blur.value = backend != "opaque"
                forceMiuix.value = backend == "miuix"
            }
            runner.waitForIdleSync()
            Thread.sleep(1500)
            var dock = android.graphics.RectF()
            runner.runOnMainSync {
                val bounds = host.semanticsNodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-dock" }.boundsInRoot
                dock = android.graphics.RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
            val shot = checkNotNull(runner.uiAutomation.takeScreenshot())
            File(folder, "$name-$backend.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val dp = host.resources.displayMetrics.density
            fun luminance(x: Float, y: Float): Int {
                val pixel = shot.getPixel(x.toInt(), y.toInt())
                return ((pixel shr 16 and 255) * 299 + (pixel shr 8 and 255) * 587 + (pixel and 255) * 114) / 1000
            }
            val page = luminance(dock.left / 2, dock.top - 60 * dp)
            val body = luminance(dock.left + 154 * dp * dock.width() / (235 * dp), dock.centerY())
            val below = luminance(dock.centerX(), dock.bottom + 6 * dp)
            val above = luminance(dock.centerX(), dock.top - 6 * dp)
            shot.recycle()
            rows += "$name/$backend page=$page body=${body - page} above=${above - page} below=${below - page}"
            if (name != "black" && body < page - LIGHT_MAX_DARKER) {
                problems += "$name/$backend capsule darker than the page by ${page - body}"
            }
            if (name != "black" && below > page - 2) problems += "$name/$backend lost its outer shadow"
        }
    }
    File(folder, "tone.txt").writeText(rows.joinToString("\n"))
    check(problems.isEmpty()) { problems.joinToString() + "; " + rows.joinToString("; ") }
    return "PASS: dock tone " + rows.joinToString("; ")
}
