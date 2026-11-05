package cafe.miafetta.tsuki.ui

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.ui.glass.MiGlassCapability
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import java.io.File

// Navigates the real app through semantics actions only and compares the top glass backend of main and secondary pages.
internal class SecondaryGlassVerification(private val runner: WidgetRenderInstrumentation, private val host: MainActivity) {
    private val folder = File(runner.targetContext.getExternalFilesDir(null), "secondary-glass").apply { mkdirs() }

    fun run(): String {
        runner.settleUi()
        val results = mutableListOf<String>()
        click { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-tab:2" }
        scrollPage("page-list:2", 1)
        results += "me=" + topGlass("me")
        for ((label, name) in listOf("数据与隐私" to "privacy", "开源许可" to "licenses")) {
            scrollPage("page-list:2", 30)
            click { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }
            awaitInteractive(label)
            scrollSecondary(label)
            runner.runOnMainSync {
                val inset = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars()).top
                val back = host.semanticsNodes().firstOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("返回") == true
                }
                check(back != null && back.boundsInRoot.top >= inset - 1) {
                    "$label back button overlaps the status bar: ${back?.boundsInRoot}"
                }
            }
            results += "$name=" + topGlass(name)
            runner.uiAutomation.shell("input keyevent BACK")
            runner.settleUi()
        }
        if (MiGlassCapability.supported) check(results.all { it.substringAfter('=') == "native" }) {
            "Top glass is not native on every page: $results"
        }
        return "PASS: top glass backend ${results.joinToString()}"
    }

    private fun topGlass(name: String): String {
        var state = ""
        runner.runOnMainSync {
            val decor = host.window.decorView
            val top = decor.miGlassViews().filter { it.isShown && it.width >= decor.width - 2 }
            state = when {
                top.any { it.visibility == View.VISIBLE && it.alpha > 0f } -> "native"
                top.isNotEmpty() -> "native-hidden"
                else -> "no-native"
            }
        }
        runner.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return state
    }

    private fun scrollPage(tag: String, steps: Int) {
        repeat(steps) {
            runner.runOnMainSync {
                val list = host.semanticsNodes().singleOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                list?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, list.boundsInRoot.height / 3)
            }
            Thread.sleep(120)
        }
        runner.settleUi()
    }

    // NavDisplay blocks input while the page transition runs; the page joins the accessibility tree once it is active.
    // The back button keeps its enabled look during the slide-in, so it is not a readiness signal.
    private fun awaitInteractive(label: String) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false
            runner.runOnMainSync {
                ready = host.semanticsNodes().any { node ->
                    node.config.getOrNull(SemanticsProperties.PaneTitle) == label &&
                        !node.config.contains(SemanticsProperties.HideFromAccessibility)
                }
            }
            if (ready) return
            Thread.sleep(100)
        }
        error("$label did not become interactive")
    }

    // Scrolls the secondary page until its scroll position moves; a swipe swallowed by a transition is repeated.
    private fun scrollSecondary(label: String) {
        val decor = host.window.decorView
        val x = decor.width / 2
        val y = decor.height * 3 / 4
        val seen = mutableListOf(scrollPosition())
        repeat(3) {
            val before = scrollPosition()
            runner.uiAutomation.shell("input swipe $x $y $x ${y - decor.height / 3} 600")
            runner.settleUi()
            seen += scrollPosition()
            if (seen.last() > before) return
        }
        error("$label did not scroll; scroll positions $seen; containers ${scrollContainers()}")
    }

    private fun scrollContainers(): List<String> {
        var values = emptyList<String>()
        runner.runOnMainSync {
            values = host.semanticsNodes().mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { range ->
                    val name = node.config.getOrNull(SemanticsProperties.TestTag) ?: node.id
                    "$name@${node.boundsInRoot.top.toInt()}=${range.value()}/${range.maxValue()}"
                }
            }
        }
        return values
    }

    private fun scrollPosition(): Float {
        var position = 0f
        runner.runOnMainSync {
            // The Me page list stays composed under the secondary page; the secondary page is drawn last.
            position = host.semanticsNodes().lastOrNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
                ?.config?.get(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f
        }
        return position
    }

    private fun click(match: (SemanticsNode) -> Boolean) {
        var clicked = false
        runner.runOnMainSync { clicked = host.clickSemantics(match) }
        check(clicked) { "Missing clickable node" }
        runner.settleUi()
    }
}
