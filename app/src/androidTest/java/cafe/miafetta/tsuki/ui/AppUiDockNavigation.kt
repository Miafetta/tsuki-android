package cafe.miafetta.tsuki.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import cafe.miafetta.tsuki.MainActivity

// Dock navigation checks beyond motion: per-page scroll retention and taps that race the page animation.
internal fun AppUiVerification.dockScrollRetention(dock: DockTouch, host: MainActivity) {
    runOnMainSync {
        fontScale.value = 1f
        page.intValue = 0
    }
    dock.assertSelected(0)
    fun overviewScroll() = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:0" }
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    var scroll = 0f
    runOnMainSync {
        val list = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:0" }
        checkNotNull(list.config.getOrNull(SemanticsActions.ScrollToIndex)?.action).invoke(3)
    }
    frames(host)
    runOnMainSync { scroll = overviewScroll() }
    dock.tap(2)
    dock.assertSelected(2)
    dock.tap(0)
    dock.assertSelected(0)
    runOnMainSync { check(scroll == overviewScroll()) { "Navigation lost scroll position" } }
}

// A second tap while the first page animation is still running must win at every gap, including the frame where the
// cancelled animation leaves the pager idle; and a tap that drifts a little past the dock's edge still selects.
internal fun AppUiVerification.dockQuickTaps(dock: DockTouch, host: MainActivity) {
    runOnMainSync { page.intValue = 0 }
    dock.assertSelected(0)
    for (gap in 0..5) {
        dock.tap(1)
        if (gap > 0) frames(host, gap)
        dock.tap(2)
        dock.assertSelected(2)
        dock.tap(0)
        dock.assertSelected(0)
    }
    val slack = 10f
    check(slack < android.view.ViewConfiguration.get(host).scaledTouchSlop) { "Drift test must stay within touch slop" }
    dock.tapDrifting(2, slack)
    dock.assertSelected(2)
    dock.tapDrifting(0, slack)
    dock.assertSelected(0)
}
