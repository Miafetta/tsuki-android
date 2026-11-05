package cafe.miafetta.tsuki.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation

// A horizontal swipe pages the real app without the dock. The page reached that way must become the app's page, otherwise
// the top action ignores taps (it only acts when the pager and the app agree on the page).
internal class PageSwipeVerification(private val runner: WidgetRenderInstrumentation, private val host: MainActivity) {
    fun run(): String {
        runner.settleUi()
        click("Overview tab") { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-tab:0" }
        swipe(toNext = true)
        click("Add") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("添加") == true }
        check(texts().any { it == "添加小部件" }) { "Add did not open the widget editor after swiping to Widgets (dock tab ${selectedTab()})" }
        check(selectedTab() == 1) { "Dock still selects tab ${selectedTab()} after swiping to Widgets" }
        click("Close") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("关闭") == true }
        check(texts().none { it == "添加小部件" }) { "Widget editor did not close" }
        swipe(toNext = false)
        check(selectedTab() == 0) { "Dock still selects tab ${selectedTab()} after swiping back to Overview" }
        return "PASS: swiping to Widgets selects its dock tab and Add opens the editor; swiping back selects Overview"
    }

    // The scenario is about what happens after the pager moves, so a swipe the system did not deliver as a page change is retried.
    private fun swipe(toNext: Boolean) {
        val action = if (toNext) "添加" else "刷新"
        for (attempt in 1..3) {
            swipeOnce(toNext)
            if (topAction() == action) return
        }
        error("Three swipes did not page the app (top action ${topAction()}, dock tab ${selectedTab()})")
    }

    private fun topAction(): String? {
        var label: String? = null
        runner.runOnMainSync {
            label = host.semanticsNodes().firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull { it == "添加" || it == "刷新" }
            }
        }
        return label
    }

    // Swipes inside this app's own window only; the shell input lands on the focused app.
    private fun swipeOnce(toNext: Boolean) {
        var focused = false
        runner.runOnMainSync { focused = host.hasWindowFocus() }
        check(focused) { "Tsuki is not the focused window; refusing to send input" }
        val decor = host.window.decorView
        val y = decor.height / 2
        val (from, to) = (decor.width * 85 / 100) to (decor.width * 15 / 100)
        runner.uiAutomation.shell(if (toNext) "input swipe $from $y $to $y 250" else "input swipe $to $y $from $y 250")
        runner.settleUi()
    }

    private fun selectedTab(): Int {
        var index = -1
        runner.runOnMainSync { index = host.selectedNavTab() }
        return index
    }

    private fun texts(): List<String> {
        var values = emptyList<String>()
        runner.runOnMainSync {
            values = host.semanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        }
        return values
    }

    private fun click(what: String, match: (SemanticsNode) -> Boolean) {
        var clicked = false
        var labels = emptyList<String>()
        runner.runOnMainSync {
            clicked = host.clickSemantics(match)
            if (!clicked) labels = host.semanticsLabels()
        }
        check(clicked) { "Missing clickable $what (dock tab ${selectedTab()}); on screen: ${labels.take(40)}" }
        runner.settleUi()
    }
}
