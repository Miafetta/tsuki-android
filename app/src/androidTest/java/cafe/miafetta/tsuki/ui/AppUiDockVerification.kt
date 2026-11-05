package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.glass.TsukiGlassHost
import cafe.miafetta.tsuki.ui.glass.tsukiBackdrop

import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.MainActivity
import java.io.File

// Real touch input on the dock tabs; every injected event is appended to the trace.
internal class DockTouch(private val ui: AppUiVerification, private val host: MainActivity) {
    val trace = StringBuilder()
    private var downTime = 0L

    fun tab(index: Int): Rect {
        var bounds = Rect.Zero
        ui.runOnMainSync {
            val node = ui.composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-tab:$index" }
            check(node.config[SemanticsProperties.Role] == Role.Tab)
            bounds = node.boundsInRoot
        }
        return bounds
    }

    fun touch(action: Int, index: Int, outside: Boolean = false, fraction: Float = 0f) {
        val b = tab(index)
        val now = android.os.SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, b.center.x + fraction * b.width,
            if (outside) b.top - 120 else b.center.y, 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
        try { check(ui.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        trace.appendLine("$now action=$action tab=$index outside=$outside fraction=$fraction")
    }

    fun tap(index: Int) {
        touch(MotionEvent.ACTION_DOWN, index)
        ui.frames(host, 2)
        touch(MotionEvent.ACTION_UP, index)
        if (android.provider.Settings.Global.getFloat(ui.targetContext.contentResolver, "animator_duration_scale", 1f) == 0f) {
            ui.frames(host, 3)
            check(ui.page.intValue == index) { "Disabled animations still delayed page selection" }
        }
    }

    // DOWN at the tab centre, then the finger drifts dy pixels above the dock before lifting. Upwards on purpose: the
    // dock sits just above the gesture bar, where the system may take a downward drift over as its own gesture.
    fun tapDrifting(index: Int, dy: Float) {
        val b = tab(index)
        val down = android.os.SystemClock.uptimeMillis()
        for ((action, y) in listOf(MotionEvent.ACTION_DOWN to b.center.y, MotionEvent.ACTION_MOVE to b.top - dy,
            MotionEvent.ACTION_UP to b.top - dy)) {
            val event = MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, b.center.x, y, 0)
                .apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { check(ui.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            trace.appendLine("drift action=$action tab=$index dy=$dy")
            ui.frames(host, 2)
        }
    }

    fun assertSelected(index: Int) {
        ui.framesUntil(host) { ui.page.intValue == index }
        ui.frames(host, 30)
        ui.runOnMainSync {
            val selected = ui.composeNodes(host).filter {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nav-tab:") == true &&
                    it.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                    it.config.getOrNull(SemanticsProperties.Selected) == true
            }
            check(selected.size == 1 && selected.single().config[SemanticsProperties.TestTag] == "nav-tab:$index")
        }
    }

    fun assertOutsideDrag(index: Int, fraction: Float, expected: Float) {
        touch(MotionEvent.ACTION_MOVE, index, outside = true, fraction = fraction)
        val expectedCenter = tab(0).center.x + expected * tab(0).width
        repeat(20) {
            ui.frames(host, 1)
            ui.runOnMainSync {
                val nodes = ui.composeNodes(host)
                val dock = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-dock" }.boundsInRoot
                val indicator = nodes.single {
                    it.config.getOrNull(SemanticsProperties.TestTag) == "nav-indicator"
                }.boundsInRoot
                check(indicator.left >= dock.left && indicator.right <= dock.right &&
                    indicator.top >= dock.top && indicator.bottom <= dock.bottom) {
                    "Dragged indicator left its dock: $indicator / $dock"
                }
                if (it == 19) {
                    check(kotlin.math.abs(indicator.center.x - expectedCenter) < 4) {
                        "Outside drag lost pointer control: $indicator / $expectedCenter"
                    }
                    check(indicator.height < dock.height * .85f) { "Outside drag lost pressed scale" }
                }
            }
        }
        check(ui.page.intValue == 1) { "Outside drag committed navigation before release" }
    }
}

internal fun AppUiVerification.verifyDockMotion(host: MainActivity) {
    val folder = File(targetContext.getExternalFilesDir(null), "nav-alignment").apply { mkdirs() }
    val dock = DockTouch(this, host)
    for (mode in listOf("LIGHT", "DARK")) for (enabled in listOf(true, false)) for (scale in listOf(1f, 1.3f)) {
        dockCombination(dock, host, folder, mode, enabled, scale)
    }
    dockScrollRetention(dock, host)
    dockQuickTaps(dock, host)
    // Match the unobstructed system Contacts backdrop; keep source pixels out of production.
    for (mode in listOf("LIGHT", "DARK")) flatDockCapture(dock, host, folder, mode)
    File(folder, "events.txt").writeText(dock.trace.toString())
    File(folder, "result.txt").writeText("PASS: 8 theme/blur/font combinations; real DOWN/UP/MOVE/CANCEL; " +
        "continuous fractional drag, outside drag retention/clamping/release commit/system cancel/return, reverse drag, " +
        "drag release/cancel; adjacent/cross/reverse/repeat; quick second tap at 0-5 frame gaps; edge drift within slop; " +
        "fixed >=48dp hit area; one selected Tab; per-page scroll retained.")
}

private fun AppUiVerification.dockCombination(dock: DockTouch, host: MainActivity, folder: File, mode: String,
    enabled: Boolean, scale: Float) {
    runOnMainSync {
        theme.value = mode
        blur.value = enabled
        fontScale.value = scale
        page.intValue = 0
    }
    dock.assertSelected(0)
    val prefix = "${mode.lowercase()}-blur-$enabled-font-$scale"
    val before = (0..2).map(dock::tab)
    dock.trace.appendLine("$prefix bounds=$before density=${host.resources.displayMetrics.density}")
    check(before.all { it.height >= 48 * host.resources.displayMetrics.density })
    capture(folder, "$prefix-rest")
    val recording = if (enabled && scale == 1f) uiAutomation.executeShellCommand(
        "screenrecord --bit-rate 16000000 --time-limit 20 ${folder.absolutePath}/$prefix.mp4") else null
    try {
        dockPressAndTaps(dock, host, folder, prefix, before)
        dockDrags(dock, host, folder, prefix)
    } finally {
        recording?.let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
    }
    check((0..2).map(dock::tab) == before)
}

private fun AppUiVerification.dockPressAndTaps(dock: DockTouch, host: MainActivity, folder: File, prefix: String,
    before: List<Rect>) {
    frames(host, 20)
    dock.touch(MotionEvent.ACTION_DOWN, 0)
    frames(host, 20)
    capture(folder, "$prefix-pressed")
    check((0..2).map(dock::tab) == before) { "Visual press shrank hit bounds" }
    dock.touch(MotionEvent.ACTION_UP, 0)
    dock.assertSelected(0)
    dock.touch(MotionEvent.ACTION_DOWN, 1)
    frames(host, 20)
    check(page.intValue == 0) { "Press committed navigation before release" }
    capture(folder, "$prefix-preview")
    dock.touch(MotionEvent.ACTION_MOVE, 1, outside = true)
    dock.touch(MotionEvent.ACTION_UP, 1, outside = true)
    dock.assertSelected(0)
    capture(folder, "$prefix-cancelled")
    dock.tap(1)
    frames(host, 6)
    capture(folder, "$prefix-mid")
    dock.assertSelected(1)
    dock.tap(0)
    dock.assertSelected(0)
    dock.tap(2)
    frames(host, 3)
    dock.tap(0)
    frames(host, 3)
    dock.tap(2)
    dock.assertSelected(2)
    dock.tap(2)
    dock.assertSelected(2)
    dock.touch(MotionEvent.ACTION_DOWN, 0)
    frames(host, 3)
    dock.touch(MotionEvent.ACTION_CANCEL, 0)
    dock.assertSelected(2)
    dock.tap(0)
    dock.assertSelected(0)
}

private fun AppUiVerification.dockDrags(dock: DockTouch, host: MainActivity, folder: File, prefix: String) {
    dock.touch(MotionEvent.ACTION_DOWN, 0)
    frames(host, 12)
    dock.touch(MotionEvent.ACTION_MOVE, 1, fraction = .5f)
    frames(host, 5)
    capture(folder, "$prefix-drag-moving")
    frames(host, 30)
    check(page.intValue == 0) { "Dragging committed a page before release" }
    val dragCenter = (dock.tab(1).center.x + dock.tab(2).center.x) / 2
    runOnMainSync {
        val indicator = composeNodes(host).single {
            it.config.getOrNull(SemanticsProperties.TestTag) == "nav-indicator"
        }.boundsInRoot
        check(kotlin.math.abs(indicator.center.x - dragCenter) < 4) {
            "Indicator snapped to a tab instead of following the pointer: $indicator / $dragCenter"
        }
    }
    capture(folder, "$prefix-drag-between")
    dock.touch(MotionEvent.ACTION_MOVE, 0)
    frames(host, 5)
    dock.touch(MotionEvent.ACTION_MOVE, 1)
    frames(host, 12)
    dock.touch(MotionEvent.ACTION_UP, 1)
    dock.assertSelected(1)
    dock.touch(MotionEvent.ACTION_DOWN, 1)
    dock.touch(MotionEvent.ACTION_MOVE, 2)
    frames(host, 5)
    dock.assertOutsideDrag(1, .5f, 1.5f)
    capture(folder, "$prefix-drag-outside-between")
    dock.assertOutsideDrag(2, .7f, 2f)
    dock.assertOutsideDrag(0, -.7f, 0f)
    capture(folder, "$prefix-drag-outside-left")
    dock.touch(MotionEvent.ACTION_UP, 0, outside = true)
    dock.assertSelected(0)
    dock.tap(1)
    dock.assertSelected(1)
    dock.touch(MotionEvent.ACTION_DOWN, 1)
    dock.touch(MotionEvent.ACTION_MOVE, 2)
    frames(host, 5)
    dock.touch(MotionEvent.ACTION_MOVE, 2, outside = true)
    dock.touch(MotionEvent.ACTION_CANCEL, 2, outside = true)
    dock.assertSelected(1)
    dock.touch(MotionEvent.ACTION_DOWN, 1)
    dock.touch(MotionEvent.ACTION_MOVE, 2)
    frames(host, 5)
    dock.assertOutsideDrag(0, -.7f, 0f)
    dock.touch(MotionEvent.ACTION_MOVE, 2)
    dock.touch(MotionEvent.ACTION_UP, 2)
    dock.assertSelected(2)
}

private fun AppUiVerification.flatDockCapture(dock: DockTouch, host: MainActivity, folder: File, mode: String) {
    runOnMainSync {
        host.setContent {
            TsukiAppTheme(mode) {
                TsukiGlassHost(true) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()
                            .background(if (mode == "LIGHT") Color(0xFFFDFDFD) else Color.Black)
                            .tsukiBackdrop())
                        TsukiNavDock(1, {}, Modifier.align(Alignment.BottomCenter).padding(bottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 2.dp))
                    }
                }
            }
        }
    }
    frames(host)
    val prefix = "flat-app-${mode.lowercase()}"
    capture(folder, "$prefix-rest")
    val recording = uiAutomation.executeShellCommand(
        "screenrecord --bit-rate 16000000 --time-limit 6 ${folder.absolutePath}/$prefix.mp4")
    try {
        frames(host, 20)
        dock.touch(MotionEvent.ACTION_DOWN, 1)
        frames(host, 20)
        capture(folder, "$prefix-pressed")
        dock.touch(MotionEvent.ACTION_MOVE, 1, fraction = .5f)
        frames(host, 5)
        capture(folder, "$prefix-drag")
        dock.touch(MotionEvent.ACTION_MOVE, 1, outside = true)
        dock.touch(MotionEvent.ACTION_UP, 1, outside = true)
    } finally { android.os.ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() } }
}
