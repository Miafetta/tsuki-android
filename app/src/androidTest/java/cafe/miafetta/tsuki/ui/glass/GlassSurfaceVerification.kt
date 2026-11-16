package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.components.GameChips
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.components.TsukiTopBar
import cafe.miafetta.tsuki.ui.awaitWindowFocus
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

// Stationary stripes make stale drawing and blur leaking below the fade measurable. Every capture and text file is made
// first: a failed check is recorded and the run goes on, a step that throws is recorded and the next one starts; the
// problems are reported together at the end, so one failure no longer hides the rest of the device round.
internal fun glassSurfaceVerification(runner: Instrumentation, host: Activity, folder: File) {
    val probe = GlassProbe(runner, host, folder)
    probe.install()
    Thread.sleep(2000) // Wait for the first window entrance before the pixel baseline.
    for (palette in listOf("LIGHT", "DARK")) probe.step("$palette palette") { probe.paletteProbe(palette) }
    probe.step("blur profiles") { probe.blurProfiles() }
    probe.step("title reference") { probe.titleReference() }
    probe.step("secondary pages") { probe.secondaryPages() }
    probe.step("recreation") { probe.recreation() }
    probe.finish()
}

// The stripe probe: a top bar, game chips and dock over a stationary backdrop, with every glass input exposed as state.
// With the chips shown the top glass is the home page header (GameHeaderFade), otherwise that of the other pages. The
// checks are in GlassProbeChecks.kt; blur profiles, secondary pages and recreation in GlassProbeCaptures.kt; the chip
// shadow and title references in GlassProbeReferences.kt.
internal class GlassProbe(val runner: Instrumentation, val host: Activity, val folder: File) {
    val mode = mutableStateOf("LIGHT")
    val enabled = mutableStateOf(true)
    val forceMiuix = mutableStateOf(false)
    val overview = mutableStateOf(true)
    val solid = mutableStateOf(false)
    // TsukiTopBar for the title captures only (titleCurve); the glass checks rely on the Miuix bar's titles.
    val tsukiTitles = mutableStateOf(false)
    var probeState: TopAppBarState? = null
    var actualProgress = 0f
    var backgroundProgress = 0f
    val density = host.resources.displayMetrics.widthPixels / 360f
    val statusTop get() = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
    // The action row bottom, where the chips' row starts.
    val actionRow get() = statusTop + (52 * density).toInt()
    val sourceY get() = statusTop + (160 * density).toInt()
    val chipY get() = statusTop + (61 * density).toInt()
    val chipX = (152 * density).toInt()
    private val problems = mutableListOf<String>()

    fun settle() {
        val done = CountDownLatch(1)
        runner.runOnMainSync { host.window.decorView.postDelayed({ done.countDown() }, 800) }
        check(done.await(3, TimeUnit.SECONDS))
    }
    fun nativeViews(): List<MiGlassView> = host.window.decorView.miGlassViews()
    // NativeTopGlass bleeds 2px past the left and right edges, so the top view is at least as wide as the window; the dock,
    // chips and buttons are far narrower.
    fun nativeTop(): MiGlassView? = nativeViews().firstOrNull { it.width >= host.window.decorView.width }
    // [focus] is the Activity whose window must be in front: the host, or its replacement after recreation.
    fun capture(name: String, focus: Activity = host): Bitmap {
        runner.awaitWindowFocus(focus)
        return checkNotNull(runner.uiAutomation.takeScreenshot()).also { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    // A failed check is recorded, and the probe goes on.
    fun expect(ok: Boolean, message: () -> String) {
        if (!ok) problems += message()
    }

    // Runs one part of the probe; an exception is recorded, the probe inputs are reset and the next part still runs.
    fun <T> step(name: String, block: () -> T): T? = try {
        block()
    } catch (failure: Throwable) {
        problems += "$name stopped: $failure"
        runCatching { reset() }
        null
    }

    private fun reset() = runner.runOnMainSync {
        forceMiuix.value = false
        enabled.value = true
        solid.value = false
        tsukiTitles.value = false
    }

    // Writes the result; fails the scenario with every recorded problem at once.
    fun finish() {
        val text = if (problems.isEmpty()) "PASS: stationary source, threshold 1/3, spring reaches 1, button redraws, native " +
            "top glass where MiGlass is supported, pixels below the game row unchanged, fallback chip matches idle; non-home " +
            "fade partly blurred at the action row and clear from 8dp below it; selected chip shadow to 12dp above and below " +
            "without a step, none below the unselected chip; LIGHT and DARK; all captures written."
            else "FAIL (${problems.size}): " + problems.joinToString(" | ")
        File(folder, "glass-probe-result.txt").writeText(text + "\n")
        check(problems.isEmpty()) { text }
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
        val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val collapsed = status + TopAppBarDefaults.CollapsedHeight
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
                TsukiGlass({ glassScrollProgress(behavior.state.collapsedFraction) }, status,
                    Modifier.fillMaxWidth().height(collapsed + if (overview.value) GameHeaderFade else TopGlassFade),
                    gameHeader = overview.value)
                val action: @Composable RowScope.() -> Unit = {
                    GlassIconButton({}, progress = progress, modifier = Modifier) { Icon(MiuixIcons.Refresh, "刷新") }
                }
                if (tsukiTitles.value) TsukiTopBar("静止采样验证", behavior, actions = action)
                else TopAppBar("静止采样验证", color = Color.Transparent, scrollBehavior = behavior, actions = action)
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
            overview.value = true
            probeState?.heightOffset = 0f
        }
        settle()
        val idleNative = idleTopGlass(name)
        val idle = capture("probe-$name-idle")
        step("$name scroll steps") { scrollSteps(name, idle, idleNative) }
        val glass = step("$name title threshold") { titleThreshold(name, idle) }
        val fallback = glass?.let { step("$name fallbacks") { fallbacks(name, idle, it) } }
        if (fallback != null) step("$name chip shadows") { chipShadows(name, idle, fallback) }
        step("$name standard fade") { standardFade(name) }
        step("$name game header") { gameHeaderFade(name) }
        step("$name solid fade") { solidFade(name) }
        if (palette == "LIGHT") step("$name title curve") { titleCurve(name) }
        runner.runOnMainSync {
            solid.value = false
            overview.value = true
        }
        idle.recycle()
        glass?.recycle()
        fallback?.recycle()
    }

    // At rest the native top glass is composed but hidden (progress 0); devices without MiGlass create no native view.
    private fun idleTopGlass(name: String): MiGlassView? {
        var top: MiGlassView? = null
        runner.runOnMainSync {
            top = nativeTop()
            if (MiGlassCapability.supported) expect(top?.alpha == 0f && top?.visibility == android.view.View.INVISIBLE) {
                "$name: idle native top glass missing or still rendering ($top)"
            } else expect(nativeViews().isEmpty()) { "$name: unsupported device created native views" }
        }
        return top
    }

    private fun solidFade(name: String) {
        runner.runOnMainSync {
            overview.value = false
            solid.value = true
            enabled.value = true
        }
        settle()
        capture("probe-$name-solid-fade").recycle()
    }
}
