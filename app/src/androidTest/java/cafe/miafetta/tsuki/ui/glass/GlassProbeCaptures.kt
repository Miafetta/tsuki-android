package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.state.AppRoute

import cafe.miafetta.tsuki.ui.pages.AccountEditor
import cafe.miafetta.tsuki.ui.pages.PlatformSelector
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.pages.LicensesPage
import cafe.miafetta.tsuki.ui.pages.PrivacyPage
import cafe.miafetta.tsuki.ui.shell

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.core.*
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Stationary frames of TsukiTopBar's titles over the solid probe background, for checking the large title curve and
// the settled small title (TitleMotion) on the device: one per collapse distance in dp, then fully collapsed once the
// small title's spring has settled. The large title is drawn TitleMotion.LargeTitleDropDp below Miuix's, so the 0dp
// frame no longer matches Miuix's own title; the fully collapsed frame still does. Restores the bar and its offset.
internal fun GlassProbe.titleCurve(name: String) {
    var restore = 0f
    runner.runOnMainSync {
        restore = checkNotNull(probeState).heightOffset
        tsukiTitles.value = true
    }
    for (distance in listOf(0f, 5f, 8.6f, 12.6f, 17.2f, 19f)) {
        runner.runOnMainSync { checkNotNull(probeState).heightOffset = -distance * density }
        settle()
        capture("probe-$name-title-${"%04.1f".format(Locale.ROOT, distance)}dp").recycle()
    }
    runner.runOnMainSync { checkNotNull(probeState).let { it.heightOffset = it.heightOffsetLimit } }
    settle()
    capture("probe-$name-title-collapsed").recycle()
    runner.runOnMainSync {
        tsukiTitles.value = false
        checkNotNull(probeState).heightOffset = restore
    }
    settle()
}

// Stripes for the blur profiles: wide enough that the strongest blur (about 9dp sigma) still leaves a measurable square wave,
// narrow enough that the fade's few pixels of sigma show at every edge.
private val ProfileStripe = 24.dp

// Stationary captures of the top bar background at full progress over black and white stripes, for calibrating the MiGlass
// radius and fade, and the miuix-blur fallback, row by row against the system profile (UI_DESIGN_CONVENTIONS.md, 玻璃范围与
// 回退): each palette and page on the preferred backend (MiGlass where supported) and on forced miuix-blur. Real display
// density and nothing drawn over the glass; the stripes are vertical, so any row below the glass is the unblurred reference.
// The checks and the recorded values per capture are in profileReading.
internal fun GlassProbe.blurProfiles() {
    val home = mutableStateOf(true)
    val miuix = mutableStateOf(false)
    runner.runOnMainSync {
        (host as ComponentActivity).setContent {
            CompositionLocalProvider(LocalForceMiuixGlass provides miuix.value) {
                TsukiAppTheme(mode.value) { ProfileContent(home.value) }
            }
        }
    }
    val rows = mutableListOf(profileGeometry())
    val blurs = MiGlassCapability.supported || isRuntimeShaderSupported()
    for (backend in listOf("preferred", "miuix")) for (palette in listOf("LIGHT", "DARK")) for (page in listOf("home", "other")) {
        runner.runOnMainSync {
            miuix.value = backend == "miuix"
            mode.value = palette
            home.value = page == "home"
        }
        settle()
        var native = false
        runner.runOnMainSync { native = nativeTop()?.visibility == android.view.View.VISIBLE }
        val shot = capture("profile-${palette.lowercase()}-$page-$backend")
        val label = "$backend ${if (native) "(MiGlass)" else "(no native view)"} $palette $page"
        rows += profileReading(shot, label, blurs && (backend == "preferred" || isRuntimeShaderSupported()))
        shot.recycle()
    }
    runner.runOnMainSync { miuix.value = false }
    File(folder, "profile-geometry.txt").writeText(rows.joinToString("\n", postfix = "\n"))
}

@Composable
private fun ProfileContent(home: Boolean) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    TsukiGlassHost(true) {
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
            Canvas(Modifier.fillMaxSize().tsukiBackdrop()) {
                val stripe = ProfileStripe.toPx()
                repeat((size.width / stripe).toInt() + 1) { i ->
                    drawRect(if (i % 2 == 0) Color.Black else Color.White, Offset(i * stripe, 0f), Size(stripe, size.height))
                }
                fun marker(index: Int) = if (profileMarkerRed(index) == 255) Color.White else Color.Black
                val edge = ProfileMarkerPx.toFloat()
                drawRect(marker(0), Offset.Zero, Size(edge, size.height))
                drawRect(marker(((size.width - 1) / stripe).toInt()), Offset(size.width - edge, 0f), Size(edge, size.height))
            }
            TsukiGlass({ 1f }, status, Modifier.fillMaxWidth().height(status + TopAppBarDefaults.CollapsedHeight +
                if (home) GameHeaderFade else TopGlassFade), gameHeader = home)
        }
    }
}

// The edge markers: 2px at the left and right of the backdrop in the inverse of the stripe under them (white over the black
// stripe 0, and so on). An outermost column that still shows its marker's value shows the unblurred source.
private const val ProfileMarkerPx = 2

private fun profileMarkerRed(stripe: Int): Int = if (stripe % 2 == 0) 255 else 0

private fun red(bitmap: android.graphics.Bitmap, x: Int, y: Int): Int = bitmap.getPixel(x, y) shr 16 and 255

// Share of the inner columns [8, width - 8) whose red lies between the stripes' black and white (24..231).
private fun midToneShare(bitmap: android.graphics.Bitmap, y: Int): Float {
    val columns = 8 until bitmap.width - 8
    return columns.count { red(bitmap, it, y) in 24..231 }.toFloat() / columns.count()
}

// One profile capture, as its profile-geometry.txt line. A row's max - min red no longer tells blur apart: the stripes are
// 78px at 3.25 px/dp, and with the miuix-blur fallback at 10dp (sigma about 15px) the stripe centres stay pure black and
// white, while one unblurred edge column alone keeps the spread at 255. So the top row must have at least 15 points more
// mid-tone columns than the clear reference row below the glass, and on the top row and half way down the status bar (both
// at full strength) the outermost columns must differ from their unblurred markers by more than 24.
private fun GlassProbe.profileReading(shot: android.graphics.Bitmap, label: String, check: Boolean): String {
    val reference = shot.height * 3 / 4
    val top = midToneShare(shot, 0)
    val clear = midToneShare(shot, reference)
    fun share(value: Float) = "%.3f".format(Locale.ROOT, value)
    if (check) expect(top >= clear + 0.15f) {
        "profile $label: top row is not blurred: mid-tone share ${share(top)} / reference ${share(clear)}"
    }
    val stripe = ProfileStripe.value * host.resources.displayMetrics.density
    val right = shot.width - 1
    val markers = listOf(0 to profileMarkerRed(0), right to profileMarkerRed((right / stripe).toInt()))
    val edges = listOf(0, statusTop / 2).joinToString("; ") { y ->
        "row $y " + markers.joinToString(", ") { (x, marker) ->
            val value = red(shot, x, y)
            if (check) expect(kotlin.math.abs(value - marker) > 24) {
                "profile $label: row $y column $x is unblurred: red $value, marker $marker"
            }
            "x $x red $value (marker $marker, reference row ${red(shot, x, reference)})"
        }
    }
    return "$label: mid-tone share row 0 = ${share(top)}, reference row $reference = ${share(clear)}; edges $edges"
}

// Rows in px from the screen top, from the same geometry as TsukiGlass, for both paths and both clear lines.
private fun GlassProbe.profileGeometry(): String {
    val scale = host.resources.displayMetrics.density
    val status = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top.toFloat()
    val collapsed = status + TopAppBarDefaults.CollapsedHeight.value * scale
    val pages = listOf("home" to GameHeaderFade, "other" to TopGlassFade).joinToString("; ") { (page, fade) ->
        val height = collapsed + fade.value * scale
        val curve = topGlassCurve(height, status, TopGlassPlateau.value * scale, TopGlassClearFade.value * scale)
        val above = if (page == "home") 0f else NativeTopGlassFadeAbove.value * scale
        val nativeFade = (nativeTopGlassFadeFrom(height, collapsed, above) * height).roundToInt()
        "$page height ${height.roundToInt()}: MiGlass fade from $nativeFade; miuix-blur full strength to " +
            "${(curve.fullUntil * height).roundToInt()}, fade from ${(curve.fadeFrom * height).roundToInt()}"
    }
    val native = "MiGlass radius $NativeTopGlassRadius (${miGlassRadiusPixels(NativeTopGlassRadius)} px)"
    val fallback = "miuix-blur radius ${TopGlassBlurRadius.value * scale} px, curve $TopGlassBlurCurve"
    return "density $scale px/dp, status bar $status px, action row bottom ${collapsed.roundToInt()} px, $native, $fallback, " +
        "stripe ${ProfileStripe.value * scale} px; $pages"
}

// Collapsed captures of the secondary pages in both palettes.
internal fun GlassProbe.secondaryPages() {
    val route = mutableStateOf<AppRoute>(AppRoute.AddAccount)
    runner.runOnMainSync {
        (host as ComponentActivity).setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, 1f), LocalForceMiuixGlass provides forceMiuix.value) {
                TsukiAppTheme(mode.value) {
                    key(route.value, mode.value) {
                        val nav = rememberNavController<AppRoute>(route.value)
                        NavDisplay(navController = nav, onBack = {}) {
                            entry<AppRoute.AddAccount> {
                                AccountEditor(host, false, "", {}, { _, _ -> error("Fixture must not save credentials") })
                            }
                            entry<AppRoute.SelectPlatform> { PlatformSelector(false) {} }
                            entry<AppRoute.Privacy> { PrivacyPage(true) {} }
                            entry<AppRoute.Licenses> { LicensesPage(true) {} }
                        }
                    }
                }
            }
        }
    }
    for (palette in listOf("LIGHT", "DARK")) {
        for ((page, name) in listOf(AppRoute.AddAccount to "account", AppRoute.SelectPlatform to "platform",
            AppRoute.Privacy to "privacy", AppRoute.Licenses to "licenses")) {
            runner.runOnMainSync {
                mode.value = palette
                route.value = page
            }
            settle()
            val x = host.window.decorView.width / 2
            val y = host.window.decorView.height * 3 / 4
            runner.uiAutomation.shell("input swipe $x $y $x ${y - 150} 700")
            settle()
            capture("$name-${palette.lowercase()}-collapsed").recycle()
        }
    }
}

// Recreate the Activity with in-memory UI only; no account/settings/widget writes.
internal fun GlassProbe.recreation() {
    val hostRunner = runner as cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
    val recreated = CountDownLatch(1)
    var replacement: ComponentActivity? = null
    hostRunner.uiContentOnCreate = { activity ->
        replacement = activity
        activity.setContent {
            TsukiAppTheme("LIGHT") {
                TsukiGlassHost(true) {
                    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                        Canvas(Modifier.fillMaxSize().tsukiBackdrop()) {
                            repeat(80) { i -> drawRect(if (i % 2 == 0) Color.Cyan else Color.Magenta,
                                Offset(i * 16.dp.toPx(), 0f), Size(16.dp.toPx(), size.height)) }
                        }
                        val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        TsukiGlass({ 1f }, status, Modifier.fillMaxWidth()
                            .height(status + TopAppBarDefaults.CollapsedHeight + GameHeaderFade), gameHeader = true)
                        TsukiNavDock(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
                    }
                }
            }
        }
        activity.window.decorView.postDelayed({ recreated.countDown() }, 800)
    }
    try {
        runner.runOnMainSync { host.recreate() }
        check(recreated.await(10, TimeUnit.SECONDS)) { "Activity recreation timed out" }
        check(replacement !== host && replacement != null)
        // The old host is destroyed by now; the replacement's window is the one in front.
        capture("probe-recreated", checkNotNull(replacement)).recycle()
        File(folder, "glass-recreation-result.txt").writeText("PASS: new Activity created and background rendered; in-memory fixture only")
    } finally {
        hostRunner.uiContentOnCreate = null
        runner.runOnMainSync { replacement?.finish() }
    }
}
