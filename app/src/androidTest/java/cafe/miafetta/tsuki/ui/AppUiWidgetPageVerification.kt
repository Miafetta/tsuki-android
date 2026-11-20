package cafe.miafetta.tsuki.ui

import android.view.MotionEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import cafe.miafetta.tsuki.MainActivity
import kotlin.math.abs

// The Widgets page below its tab row and the widget sheet's settings rows, in the page fixture's 360dp-wide density.

private fun AppUiVerification.node(host: MainActivity, match: (SemanticsNode) -> Boolean): SemanticsNode? {
    var found: SemanticsNode? = null
    runOnMainSync { found = composeNodes(host).firstOrNull(match) }
    return found
}

private fun AppUiVerification.bounds(host: MainActivity, match: (SemanticsNode) -> Boolean): Rect =
    checkNotNull(node(host, match)) { "Node missing" }.boundsInRoot

// The first preview starts 24dp below the tab row (the list's 10dp and the gallery's 14dp, which hold the preview's
// shadow, C14). A press anywhere on an entry, here on its name, sinks the preview alone; a system cancel opens nothing.
internal fun AppUiVerification.widgetEntryLayout(touch: TabRowTouch, host: MainActivity) {
    val dp = host.resources.displayMetrics.widthPixels / 360f
    fun image() = bounds(host) { it.tag?.startsWith("widget-image:") == true }
    val rest = image()
    val gap = rest.top - touch.row().bottom
    touch.trace.appendLine("entry rest image=$rest gap=$gap dp=$dp")
    check(abs(gap - 24 * dp) <= 2) { "Tab row to first preview: $gap px, expected ${24 * dp}" }
    val entry = checkNotNull(node(host) { it.tag?.startsWith("widget-preview:") == true }) { "Entry missing" }
    val nameNode = checkNotNull(entry.descendants().firstOrNull { it.text != null && it.boundsInRoot.top >= rest.bottom }) {
        "Entry name missing"
    }
    val name = nameNode.boundsInRoot
    touch.touch(MotionEvent.ACTION_DOWN, name.center.x, name.center.y)
    frames(host, 20)
    val pressed = image()
    val pressedName = bounds(host) { it.text == nameNode.text }
    touch.trace.appendLine("entry pressed image=$pressed name=$name/$pressedName")
    check(pressed.width < rest.width * .97f && abs(pressed.center.y - rest.center.y) <= 1) { "The preview did not sink" }
    check(abs(pressedName.left - name.left) <= 1 && abs(pressedName.width - name.width) <= 1) { "The name moved with the press" }
    touch.touch(MotionEvent.ACTION_CANCEL, name.center.x, name.center.y)
    frames(host, 30)
    check(abs(image().width - rest.width) <= 1) { "The preview did not spring back" }
    check(dialog.value.isEmpty()) { "A cancelled press opened the sheet" }
}

// The settings rows span the sheet, so their press highlight crosses the screen (C17), and keep their text 25dp in.
internal fun AppUiVerification.verifySheetRows(host: MainActivity) {
    val dp = host.resources.displayMetrics.widthPixels / 360f
    val width = host.window.decorView.width.toFloat()
    for (title in listOf("角色", "配色")) {
        val row = bounds(host) { it.tag == "dropdown:$title" }
        check(row.left <= 1 && row.right >= width - 1) { "Settings row $title does not span the sheet: $row" }
        val text = bounds(host) { it.text == title }
        check(abs(text.left - row.left - 25 * dp) <= 2) { "Settings row $title text at ${text.left - row.left} px" }
    }
}
