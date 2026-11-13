package cafe.miafetta.tsuki.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.*
import cafe.miafetta.tsuki.MainActivity
import java.io.File
import kotlin.math.abs

// Real touch on the widgets page tab row (C7, TsukiTabRow) in the page fixture; every injected event is traced.
internal class TabRowTouch(private val ui: AppUiVerification, private val host: MainActivity) {
    val trace = StringBuilder()
    private var downTime = 0L

    fun bounds(tag: String): Rect {
        var bounds = Rect.Zero
        ui.runOnMainSync {
            bounds = ui.composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.boundsInRoot
        }
        return bounds
    }

    fun row() = bounds("catalog-tabs")
    fun tab(index: Int) = bounds("catalog-tab:$index")
    fun indicator() = bounds("catalog-indicator")

    fun touch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try { check(ui.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        trace.appendLine("$now action=$action x=$x y=$y catalog=${ui.catalog.intValue} page=${ui.page.intValue}")
    }

    // Equal steps one frame apart, so the list and the pager see a drag rather than a jump.
    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int = 8) {
        for (step in 1..steps) {
            touch(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * step / steps, fromY + (toY - fromY) * step / steps)
            ui.frames(host, 1)
        }
    }

    // Waits for the indicator's centre to reach x, then checks that it stays inside the row.
    fun indicatorAt(x: Float, what: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (abs(indicator().center.x - x) >= 4 && SystemClock.uptimeMillis() < deadline) ui.frames(host, 6)
        val indicator = indicator()
        val row = row()
        trace.appendLine("$what: indicator=$indicator row=$row expected=$x")
        check(abs(indicator.center.x - x) < 4) { "$what: indicator at ${indicator.center.x}, expected $x" }
        check(indicator.left >= row.left - 1 && indicator.right <= row.right + 1) { "$what: indicator left the row" }
    }

    fun pressedShape(what: String) = check(indicator().height < tab(0).height * .95f) { "$what: indicator lost its press" }
}

internal fun AppUiVerification.verifyWidgetTabs(host: MainActivity): String {
    val folder = File(targetContext.getExternalFilesDir(null), "widget-tabs").apply { mkdirs() }
    var focused = false
    runOnMainSync { focused = host.hasWindowFocus() }
    check(focused) { "Tsuki is not the focused window; refusing to inject touches" }
    val touch = TabRowTouch(this, host)
    runOnMainSync {
        catalog.intValue = 0
        page.intValue = 1
    }
    // The pager composes the Widgets page off screen beside Overview; wait until it has settled on screen.
    var shown = Rect.Zero
    framesUntil(host) {
        val now = touch.row()
        val settled = now == shown && now.left >= 0 && now.left < host.window.decorView.width / 4f
        shown = now
        settled
    }
    try {
        tabRowRest(touch, host, folder)
        tabRowTaps(touch, host)
        tabRowPress(touch, host, folder)
        tabRowDrags(touch, host, folder)
        tabRowNoPaging(touch, host)
        tabRowVertical(touch, host)
        tabRowOutsideChanges(touch, host)
    } finally { File(folder, "events.txt").writeText(touch.trace.toString()) }
    val result = "PASS: rest geometry and Tab semantics; real taps; press preview without switching cards; system cancel; " +
        "drag between tabs, outside the row and back, committed on release; sideways drags past the row keep the page; " +
        "vertical drag drops the preview and scrolls the page; accessibility clicks; empty-state switch animates."
    File(folder, "result.txt").writeText(result)
    return result
}

// C7 at rest in the fixture's 360dp-wide density: 45dp row, 5dp contour and gap, equal 35dp tabs, indicator on its tab.
private fun AppUiVerification.tabRowRest(touch: TabRowTouch, host: MainActivity, folder: File) {
    val dp = host.resources.displayMetrics.widthPixels / 360f
    val row = touch.row()
    val first = touch.tab(0)
    val second = touch.tab(1)
    val indicator = touch.indicator()
    touch.trace.appendLine("rest row=$row tabs=$first/$second indicator=$indicator dp=$dp")
    check(abs(row.height - 45 * dp) <= 1.5f && abs(first.height - 35 * dp) <= 1.5f) { "Row or tab height changed" }
    check(abs(first.width - second.width) <= 1 && abs(first.left - row.left - 5 * dp) <= 1.5f &&
        abs(second.left - first.right - 5 * dp) <= 1.5f) { "Contour, gap or tab widths changed" }
    check(abs(indicator.left - first.left) <= 1 && abs(indicator.right - first.right) <= 1 &&
        abs(indicator.height - first.height) <= 1) { "Indicator does not cover the selected tab at rest" }
    runOnMainSync {
        val nodes = composeNodes(host)
        val tabs = nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("catalog-tab:") == true }
        check(tabs.size == 2 && tabs.all { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab })
        check(tabs.single { it.config.getOrNull(SemanticsProperties.Selected) == true }
            .config[SemanticsProperties.TestTag] == "catalog-tab:0")
        val group = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "catalog-tabs" }.config
        check(SemanticsProperties.SelectableGroup in group && group[SemanticsProperties.CollectionInfo].columnCount == 2)
    }
    check(find("已添加 1", exact = true) != null && find("全部小部件", exact = true, clickable = true) != null)
    for (mode in listOf("LIGHT", "DARK")) {
        runOnMainSync { theme.value = mode }
        frames(host, 30)
        capture(folder, "rest-${mode.lowercase()}")
        val tab = touch.tab(1)
        touch.touch(MotionEvent.ACTION_DOWN, tab.center.x, tab.center.y)
        touch.indicatorAt(tab.center.x, "pressed capture $mode")
        capture(folder, "pressed-${mode.lowercase()}")
        touch.touch(MotionEvent.ACTION_CANCEL, tab.center.x, tab.center.y)
        touch.indicatorAt(touch.tab(0).center.x, "cancelled capture $mode")
    }
    runOnMainSync { theme.value = "LIGHT" }
    frames(host, 30)
    check(catalog.intValue == 0) { "A cancelled press switched the tab" }
}

private fun AppUiVerification.tabRowTaps(touch: TabRowTouch, host: MainActivity) {
    for (index in listOf(1, 0, 1, 0)) {
        val tab = touch.tab(index)
        touch.touch(MotionEvent.ACTION_DOWN, tab.center.x, tab.center.y)
        frames(host, 2)
        touch.touch(MotionEvent.ACTION_UP, tab.center.x, tab.center.y)
        framesUntil(host) { catalog.intValue == index }
        touch.indicatorAt(touch.tab(index).center.x, "tap $index")
    }
}

// Card tags below the row: "widget-preview:widget:…" for 已添加, "widget-preview:catalog:…" for 全部小部件.
private fun AppUiVerification.cards(host: MainActivity): List<String> {
    var tags = emptyList<String>()
    runOnMainSync {
        tags = composeNodes(host).mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith("widget-preview:") }
        }
    }
    return tags
}

private fun AppUiVerification.tabRowPress(touch: TabRowTouch, host: MainActivity, folder: File) {
    val second = touch.tab(1)
    touch.touch(MotionEvent.ACTION_DOWN, second.center.x, second.center.y)
    touch.indicatorAt(second.center.x, "press preview")
    frames(host, 20)
    touch.pressedShape("press preview")
    check(catalog.intValue == 0 && cards(host).none { it.startsWith("widget-preview:catalog:") }) {
        "Pressing switched the cards before release: ${cards(host)}"
    }
    capture(folder, "preview-light")
    touch.touch(MotionEvent.ACTION_UP, second.center.x, second.center.y)
    framesUntil(host) { catalog.intValue == 1 }
    framesUntil(host) { cards(host).let { it.isNotEmpty() && it.all { tag -> tag.startsWith("widget-preview:catalog:") } } }
    // A system cancel returns the indicator to the actual selection without committing.
    val first = touch.tab(0)
    touch.touch(MotionEvent.ACTION_DOWN, first.center.x, first.center.y)
    touch.indicatorAt(first.center.x, "press before cancel")
    touch.touch(MotionEvent.ACTION_CANCEL, first.center.x, first.center.y)
    touch.indicatorAt(touch.tab(1).center.x, "system cancel")
    frames(host, 20)
    check(catalog.intValue == 1) { "System cancel committed the pressed tab" }
}

private fun AppUiVerification.tabRowDrags(touch: TabRowTouch, host: MainActivity, folder: File) {
    val first = touch.tab(0)
    val second = touch.tab(1)
    val y = second.center.y
    val middle = (first.center.x + second.center.x) / 2
    touch.touch(MotionEvent.ACTION_DOWN, second.center.x, y)
    frames(host, 6)
    touch.drag(second.center.x, y, middle, y)
    touch.indicatorAt(middle, "drag between tabs")
    touch.pressedShape("drag between tabs")
    check(catalog.intValue == 1) { "Dragging switched the cards before release" }
    capture(folder, "drag-light")
    touch.drag(middle, y, first.center.x, y)
    touch.touch(MotionEvent.ACTION_UP, first.center.x, y)
    framesUntil(host) { catalog.intValue == 0 }
    touch.indicatorAt(touch.tab(0).center.x, "drag release")
    // Once sideways, leaving the row keeps control, clamped to the row, and releasing out there still commits.
    val rowBefore = touch.row()
    val above = rowBefore.top - 120
    touch.touch(MotionEvent.ACTION_DOWN, first.center.x, y)
    touch.drag(first.center.x, y, second.center.x, y, steps = 4)
    touch.drag(second.center.x, y, second.center.x + second.width * .7f, above)
    touch.indicatorAt(second.center.x, "outside drag past the last tab")
    touch.pressedShape("outside drag")
    touch.drag(second.center.x + second.width * .7f, above, first.center.x - first.width, above)
    touch.indicatorAt(first.center.x, "outside drag past the first tab")
    check(catalog.intValue == 0 && touch.row() == rowBefore) { "Outside drag switched cards or scrolled the page" }
    touch.drag(first.center.x - first.width, above, second.center.x, above, steps = 6)
    touch.indicatorAt(second.center.x, "outside drag back over the tabs")
    touch.touch(MotionEvent.ACTION_UP, second.center.x, above)
    framesUntil(host) { catalog.intValue == 1 }
}

// Long sideways drags that leave the row towards the screen edges: the pager must stay on Widgets throughout.
private fun AppUiVerification.tabRowNoPaging(touch: TabRowTouch, host: MainActivity) {
    val width = host.window.decorView.width.toFloat()
    for ((from, to) in listOf(1 to 0, 0 to 1)) {
        val start = touch.tab(from)
        val before = touch.row()
        val end = if (to == 0) width * .04f else width * .96f
        touch.touch(MotionEvent.ACTION_DOWN, start.center.x, start.center.y)
        touch.drag(start.center.x, start.center.y, end, start.center.y, steps = 12)
        frames(host, 10)
        var dock = -1
        runOnMainSync { dock = host.selectedNavTab() }
        check(abs(touch.row().left - before.left) <= 1 && page.intValue == 1 && dock == 1) {
            "Sideways drag on the tabs moved the pager: ${touch.row()} / $before, page ${page.intValue}, dock $dock"
        }
        touch.indicatorAt(touch.tab(to).center.x, "long sideways drag to $to")
        touch.touch(MotionEvent.ACTION_UP, end, start.center.y)
        framesUntil(host) { catalog.intValue == to }
        frames(host, 40)
        check(page.intValue == 1 && abs(touch.row().left - before.left) <= 1) { "Releasing a sideways drag paged the app" }
    }
}

// A press that first moves vertically drops its preview and scrolls the page (list, then the collapsing top bar).
private fun AppUiVerification.tabRowVertical(touch: TabRowTouch, host: MainActivity) {
    check(catalog.intValue == 1) { "The vertical case needs the longer 全部小部件 list" }
    val dp = host.resources.displayMetrics.widthPixels / 360f
    val first = touch.tab(0)
    val before = touch.row()
    touch.touch(MotionEvent.ACTION_DOWN, first.center.x, first.center.y)
    touch.indicatorAt(first.center.x, "press before scrolling")
    // An eighth of the screen keeps the row on screen (the list disposes items that leave it).
    val top = first.center.y - host.window.decorView.height / 8f
    touch.drag(first.center.x, first.center.y, first.center.x, top, steps = 10)
    touch.indicatorAt(touch.tab(1).center.x, "vertical drag dropped the preview")
    val moved = before.top - touch.row().top
    check(moved > 16 * dp) { "A vertical drag on the tabs did not scroll the page: $moved px" }
    touch.touch(MotionEvent.ACTION_UP, first.center.x, top)
    frames(host, 40)
    check(catalog.intValue == 1) { "A vertical drag committed the pressed tab" }
    // Back to the top with a real drag on the cards, so the top bar expands again through nested scrolling.
    val x = host.window.decorView.width / 2f
    val from = touch.row().bottom + 80 * dp
    touch.touch(MotionEvent.ACTION_DOWN, x, from)
    touch.drag(x, from, x, from + host.window.decorView.height / 3f, steps = 10)
    touch.touch(MotionEvent.ACTION_UP, x, from + host.window.decorView.height / 3f)
    framesUntil(host) { abs(touch.row().top - before.top) <= 2 }
}

// Selection changed from outside the row: accessibility clicks, and the empty state's 浏览全部小部件 button.
private fun AppUiVerification.tabRowOutsideChanges(touch: TabRowTouch, host: MainActivity) {
    for (index in listOf(0, 1, 0)) {
        var clicked = false
        runOnMainSync {
            clicked = host.clickSemantics { it.config.getOrNull(SemanticsProperties.TestTag) == "catalog-tab:$index" }
        }
        check(clicked) { "catalog-tab:$index has no click action" }
        framesUntil(host) { catalog.intValue == index }
        touch.indicatorAt(touch.tab(index).center.x, "accessibility click $index")
    }
    runOnMainSync { emptyState.value = true }
    frames(host, 30)
    val first = touch.tab(0).center.x
    val second = touch.tab(1).center.x
    click("浏览全部小部件")
    val samples = (1..12).map {
        frames(host, 1)
        touch.indicator().center.x
    }
    framesUntil(host) { catalog.intValue == 1 }
    touch.indicatorAt(second, "empty state button")
    touch.trace.appendLine("empty-state samples=$samples")
    val animated = android.provider.Settings.Global.getFloat(targetContext.contentResolver, "animator_duration_scale", 1f) != 0f
    check(!animated || samples.any { it > first + 4 && it < second - 4 }) { "Indicator jumped on an outside change: $samples" }
    runOnMainSync {
        emptyState.value = false
        catalog.intValue = 0
    }
    frames(host)
}
