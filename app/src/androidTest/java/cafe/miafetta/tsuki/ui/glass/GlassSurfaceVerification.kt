package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.components.GameChips
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.miGlassViews

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
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
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Stationary stripes make stale drawing and blur leaking below the fade measurable.
internal fun glassSurfaceVerification(runner: Instrumentation, host: Activity, folder: File) {
    val probe = GlassProbe(runner, host, folder)
    probe.install()
    Thread.sleep(2000) // Wait for the first window entrance before the pixel baseline.
    for (palette in listOf("LIGHT", "DARK")) probe.paletteProbe(palette)
    File(folder, "glass-probe-result.txt").writeText("PASS: stationary source, threshold 1/3, spring reaches 1, button redraws, " +
        "pixels below collapsed+46dp unchanged, fallback chip matches idle; non-home fade remains visible at 8dp and clears after 16dp; " +
        "LIGHT and DARK.")
    probe.secondaryPages()
    probe.recreation()
}

// The stripe probe: a top bar, game chips and dock over a stationary backdrop, with every glass input exposed as state.
// Secondary pages and recreation are in GlassProbeCaptures.kt.
internal class GlassProbe(val runner: Instrumentation, val host: Activity, val folder: File) {
    val mode = mutableStateOf("LIGHT")
    private val enabled = mutableStateOf(true)
    val forceMiuix = mutableStateOf(false)
    private val overview = mutableStateOf(true)
    private val solid = mutableStateOf(false)
    private val gameHeader = mutableStateOf(false)
    private var probeState: TopAppBarState? = null
    private var actualProgress = 0f
    private var backgroundProgress = 0f
    val density = host.resources.displayMetrics.widthPixels / 360f
    private val statusTop get() = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
    private val sourceY get() = statusTop + (160 * density).toInt()
    private val chipY get() = statusTop + (61 * density).toInt()
    private val chipX = (152 * density).toInt()

    fun settle() {
        val done = CountDownLatch(1)
        runner.runOnMainSync { host.window.decorView.postDelayed({ done.countDown() }, 800) }
        check(done.await(3, TimeUnit.SECONDS))
    }
    private fun nativeViews(): List<MiGlassView> = host.window.decorView.miGlassViews()
    private fun nativeTop(): MiGlassView? = nativeViews().firstOrNull { it.width == host.window.decorView.width }
    fun capture(name: String): Bitmap = checkNotNull(runner.uiAutomation.takeScreenshot()).also { bitmap ->
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Color difference between two neighbouring stripes on row y: low means the stripes are blurred together.
    private fun contrast(bitmap: Bitmap, y: Int): Int {
        val a = bitmap.getPixel((128 * density).toInt(), y)
        val b = bitmap.getPixel((136 * density).toInt(), y)
        return listOf(16, 8, 0).sumOf { shift -> kotlin.math.abs((a shr shift and 255) - (b shr shift and 255)) }
    }

    fun install() = runner.runOnMainSync {
        (host as ComponentActivity).setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, 1f), LocalForceMiuixGlass provides forceMiuix.value) {
                TsukiAppTheme(mode.value) { ProbeContent() }
            }
        }
    }

    @Composable
    private fun ProbeContent() {
        val behavior = MiuixScrollBehavior()
        SideEffect { probeState = behavior.state }
        val visible by remember(behavior) { derivedStateOf { glassVisible(behavior.state.collapsedFraction) } }
        val progress = animateGlassVisibility(visible).value
        val background = glassScrollProgress(behavior.state.collapsedFraction)
        SideEffect { backgroundProgress = background }
        SideEffect { actualProgress = progress }
        val collapsed = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopAppBarDefaults.CollapsedHeight
        TsukiGlassHost(enabled.value) {
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                Canvas(Modifier.fillMaxSize().tsukiBackdrop()) {
                    val stripe = 8.dp.toPx()
                    repeat((size.width / stripe).toInt() + 1) { i ->
                        drawRect(if (i % 2 == 0) Color(0xFF27B9DD) else Color(0xFFCE496C),
                            Offset(i * stripe, 0f), Size(stripe, size.height))
                    }
                    if (solid.value) drawRect(Color(0xFF777777))
                }
                TsukiGlass({ glassScrollProgress(behavior.state.collapsedFraction) },
                    collapsed - if (overview.value) 0.dp else TopGlassFadeInset,
                    Modifier.fillMaxWidth().height(collapsed + if (gameHeader.value) GameHeaderFade
                        else if (overview.value) GameChipGlassFade else TopGlassFade), gameHeader = gameHeader.value)
                TopAppBar("静止采样验证", color = Color.Transparent, scrollBehavior = behavior, actions = {
                    GlassIconButton({}, progress = progress, modifier = Modifier) { Icon(MiuixIcons.Refresh, "刷新") }
                })
                if (overview.value) GameChips(listOf("selected" to "选中", "plain" to "未选中"), "selected", {},
                    Modifier.padding(top = collapsed)) { glassScrollProgress(behavior.state.collapsedFraction) }
                TsukiNavDock(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
            }
        }
    }

    fun paletteProbe(palette: String) {
        val name = palette.lowercase()
        runner.runOnMainSync {
            mode.value = palette
            enabled.value = true
            probeState?.heightOffset = 0f
        }
        settle()
        var idleNative: MiGlassView? = null
        runner.runOnMainSync {
            idleNative = nativeTop()
            if (MiGlassCapability.supported) check(idleNative?.alpha == 0f && idleNative?.visibility == android.view.View.INVISIBLE) {
                "Idle native top still renders"
            }
            else check(nativeViews().isEmpty()) { "Unsupported device created native views" }
        }
        val idle = capture("probe-$name-idle")
        scrollSteps(name, idle, idleNative)
        val glass = titleThreshold(name, idle)
        val fallback = fallbacks(name, idle, glass)
        standardFade(name)
        gameHeaderFade(name)
        runner.runOnMainSync {
            gameHeader.value = false
            overview.value = false
            solid.value = true
            enabled.value = true
        }
        settle()
        capture("probe-$name-solid-fade").recycle()
        runner.runOnMainSync { solid.value = false }
        runner.runOnMainSync { overview.value = true }
        idle.recycle()
        glass.recycle()
        fallback.recycle()
    }

    // Below the title threshold the background and chips redraw at every step and the native top view is reused.
    private fun scrollSteps(name: String, idle: Bitmap, idleNative: MiGlassView?) {
        val sampleX = (40 * density).toInt()
        var previousPixel = idle.getPixel(sampleX, (statusTop + 10 * density).toInt())
        var previousChipPixel = idle.getPixel(chipX, chipY)
        for ((index, fraction) in listOf(1f / 12f, 1f / 6f, 1f / 4f).withIndex()) {
            runner.runOnMainSync { checkNotNull(probeState).let { it.heightOffset = it.heightOffsetLimit * fraction } }
            settle()
            check(backgroundProgress > 0f && backgroundProgress < 1f) { "Background still switches at the title threshold" }
            runner.runOnMainSync {
                if (idleNative != null) check(nativeTop() === idleNative && nativeTop()?.alpha == 1f &&
                    nativeTop()?.visibility == android.view.View.VISIBLE) {
                    "Native top was recreated or not restored when scrolling"
                }
            }
            val step = capture("probe-$name-scroll-$index")
            val pixel = step.getPixel(sampleX, (statusTop + 10 * density).toInt())
            check(pixel != previousPixel) { "Background did not redraw at scroll step $index" }
            val chipPixel = step.getPixel(chipX, chipY)
            check(chipPixel != previousChipPixel) { "Chip did not follow the header at scroll step $index" }
            previousChipPixel = chipPixel
            previousPixel = pixel
            step.recycle()
        }
    }

    // Just past the 1/3 title threshold the glass is at full strength over unchanged content; returns that capture.
    private fun titleThreshold(name: String, idle: Bitmap): Bitmap {
        runner.runOnMainSync {
            val state = checkNotNull(probeState)
            check(state.heightOffsetLimit < 0f && state.heightOffsetLimit > -host.window.decorView.height) {
                "TopAppBar was not measured: ${state.heightOffsetLimit}"
            }
            state.heightOffset = state.heightOffsetLimit * (1f / 3f + .001f)
        }
        settle()
        val glass = capture("probe-$name-title-threshold")
        check(actualProgress == 1f) {
            "Glass did not reach full strength with stationary content: $actualProgress, fraction=${probeState?.collapsedFraction}"
        }
        val buttonY = statusTop + (26 * density).toInt()
        val buttonX = (host.window.decorView.width - 46 * density).toInt()
        check(idle.getPixel(buttonX, buttonY) != glass.getPixel(buttonX, buttonY)) { "Button backdrop failed to redraw without a scroll" }
        check(contrast(glass, 0) < contrast(glass, sourceY) / 2) {
            "First physical pixel row exposes unblurred source stripes"
        }
        check(contrast(glass, statusTop + (8 * density).toInt()) < contrast(glass, sourceY) / 2) {
            "Top bar tint appeared without blurring source stripes"
        }
        check(contrast(glass, chipY) < contrast(glass, sourceY) / 2) { "Chip tint appeared without blurring the source stripes" }
        // No geometry or source movement: below collapsed + 46dp must remain pixel-identical.
        val bottomY = statusTop + ((52 + 46 + 8) * density).toInt()
        for (y in bottomY until bottomY + 40) for (x in 0 until glass.width)
            check(idle.getPixel(x, y) == glass.getPixel(x, y)) { "Blur leaked below its fade at $x,$y" }
        return glass
    }

    // The forced Miuix fallback still blurs; with blur off the chip matches idle. Returns the blur-off capture.
    private fun fallbacks(name: String, idle: Bitmap, glass: Bitmap): Bitmap {
        runner.runOnMainSync { forceMiuix.value = true }
        settle()
        runner.runOnMainSync { check(nativeViews().isEmpty()) { "Forced fallback still created AndroidViews" } }
        val forced = capture("probe-$name-forced-miuix")
        check(contrast(forced, statusTop + (8 * density).toInt()) < contrast(forced, sourceY) / 2) {
            "Fallback lost the recorded backdrop after native consumers detached"
        }
        check(contrast(forced, 0) < contrast(forced, sourceY) / 2) { "Fallback exposes an unblurred first row" }
        forced.recycle()
        runner.runOnMainSync {
            forceMiuix.value = false
            enabled.value = false
        }
        settle()
        val fallback = capture("probe-$name-blur-off")
        check(idle.getPixel(chipX, chipY) == fallback.getPixel(chipX, chipY)) { "Fallback chip does not match its idle color" }
        check(glass.getPixel(chipX, chipY) != fallback.getPixel(chipX, chipY)) { "Glass chip failed to sample the colored background" }
        return fallback
    }

    private fun standardFade(name: String) {
        runner.runOnMainSync {
            overview.value = false
            enabled.value = true
        }
        settle()
        val standard = capture("probe-$name-standard-fade")
        check(contrast(standard, 0) < contrast(standard, sourceY) / 2) { "Non-home top pixel row is unblurred" }
        val clearX = (40 * density).toInt()
        check(standard.getPixel(clearX, statusTop + (30 * density).toInt()) !=
            standard.getPixel(clearX, statusTop + (10 * density).toInt())) { "Non-home fade did not start inside the title row" }
        val fadeMiddle = statusTop + ((52 + 8) * density).toInt()
        check(contrast(standard, fadeMiddle) < contrast(standard, sourceY)) { "Non-home fade ended before its extended tail" }
        val fadeEnd = statusTop + ((52 + 16 + 2) * density).toInt()
        // The overview idle image has chips here; compare against unobstructed source stripes.
        for (y in fadeEnd until fadeEnd + 40) for (x in 0 until standard.width)
            check(standard.getPixel(x, sourceY) == standard.getPixel(x, y)) { "Non-home blur leaked below 16dp at $x,$y" }
        runner.runOnMainSync { enabled.value = false }
        settle()
        capture("probe-$name-standard-fallback").recycle()
        standard.recycle()
    }

    private fun gameHeaderFade(name: String) {
        runner.runOnMainSync {
            gameHeader.value = true
            overview.value = true
            enabled.value = true
        }
        settle()
        runner.runOnMainSync {
            if (MiGlassCapability.supported) check(nativeTop()?.visibility == android.view.View.VISIBLE) {
                "Game header did not prefer native glass"
            }
        }
        val game = capture("probe-$name-game-header")
        check(contrast(game, 0) < contrast(game, sourceY) / 2) { "Game header exposes an unblurred first row" }
        val gameEnd = statusTop + ((52 + GameHeaderFade.value + 2) * density).toInt()
        for (y in gameEnd until gameEnd + 40) for (x in 0 until game.width)
            check(game.getPixel(x, y) == game.getPixel(x, sourceY)) { "Game header leaked below its fade at $x,$y" }
        game.recycle()
    }
}
