package cafe.miafetta.tsuki.ui

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import cafe.miafetta.tsuki.ui.glass.MiGlassView
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation

// Helpers shared by the device scenarios. Semantics reads and actions must run on the main thread.

internal fun View.semanticsNodes(): List<SemanticsNode> = when (this) {
    is ViewRootForTest -> semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
    is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).semanticsNodes() }
    else -> emptyList()
}

internal fun Activity.semanticsNodes(): List<SemanticsNode> = window.decorView.semanticsNodes()

// Invokes the click action of the first matching node, or of its nearest clickable ancestor. Returns false when nothing matches,
// so callers can fail off the main thread with a readable message instead of crashing the app process.
internal fun Activity.clickSemantics(match: (SemanticsNode) -> Boolean): Boolean {
    val nodes = semanticsNodes()
    val target = nodes.firstOrNull { match(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
        ?: nodes.firstOrNull(match)?.let { node ->
            generateSequence(node) { it.parent }.firstOrNull { it.config.getOrNull(SemanticsActions.OnClick) != null }
        }
    val action = target?.config?.getOrNull(SemanticsActions.OnClick)?.action ?: return false
    action.invoke()
    return true
}

// Content descriptions and texts currently on screen, for failure messages.
internal fun Activity.semanticsLabels(): List<String> = semanticsNodes().flatMap { node ->
    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
}.distinct()

// Index of the selected bottom dock tab, or -1.
internal fun Activity.selectedNavTab(): Int = semanticsNodes().firstOrNull { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nav-tab:") == true &&
        node.config.getOrNull(SemanticsProperties.Selected) == true
}?.config?.getOrNull(SemanticsProperties.TestTag)?.substringAfter(':')?.toInt() ?: -1

internal fun View.miGlassViews(): List<MiGlassView> = when (this) {
    is MiGlassView -> listOf(this)
    is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).miGlassViews() }
    else -> emptyList()
}

// A screenshot shows Tsuki only while its window has focus: a pulled-down notification shade or a system dialog takes it.
// Reads the focus on the main thread every 200ms for up to 10s. Only for captures without Tsuki's own popups or dialogs.
internal fun Instrumentation.awaitWindowFocus(host: Activity) {
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (true) {
        var focused = false
        runOnMainSync { focused = host.hasWindowFocus() }
        if (focused) return
        check(SystemClock.uptimeMillis() < deadline) {
            "Tsuki lost window focus (a system panel or dialog is over it); screenshot not taken"
        }
        Thread.sleep(200)
    }
}

internal fun UiAutomation.shell(command: String) {
    android.os.ParcelFileDescriptor.AutoCloseInputStream(executeShellCommand(command)).use { it.readBytes() }
}

// Waits for Compose and the window to go idle, then for HyperOS window animations.
internal fun WidgetRenderInstrumentation.settleUi() {
    waitForIdleSync()
    uiAutomation.waitForIdle(800, 8000)
    Thread.sleep(800)
}

// Waits long enough for the accessibility tree to reflect the new UI, then drops cached nodes.
internal fun WidgetRenderInstrumentation.settleAccessibility() {
    waitForIdleSync()
    Thread.sleep(1200)
    uiAutomation.clearCache()
}
