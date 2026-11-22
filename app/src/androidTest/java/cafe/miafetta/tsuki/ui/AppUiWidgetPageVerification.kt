package cafe.miafetta.tsuki.ui

import android.view.MotionEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import cafe.miafetta.tsuki.MainActivity
import java.io.File
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

// New-widget sheet carousel: touch-on-card shrink + spring rebound, outside drag without shrink, 3D swipe and palette sync.
internal fun AppUiVerification.widgetSheetPagerVerification(touch: TabRowTouch, host: MainActivity, folder: File) {
    val dp = host.resources.displayMetrics.widthPixels / 360f
    val width = host.window.decorView.width.toFloat()
    runOnMainSync {
        configTheme.value = "LIGHT"
        dialog.value = "widget-new"
    }
    framesUntil(host) { node(host) { it.tag == "config-preview:LIGHT" } != null }
    frames(host, 40)
    check(node(host) { it.tag == "widget-pager" } != null && node(host) { it.tag == "widget-pager-dots" } != null)
    val rest = bounds(host) { it.tag == "config-preview:LIGHT" }
    capture(folder, "sheet-light")
    // Touch directly on card: shrinks and holds while stationary inside touch slop, then springs back during swipe.
    val onCardX = rest.right - rest.width * .12f
    touch.touch(MotionEvent.ACTION_DOWN, onCardX, rest.center.y)
    frames(host, 12)
    val pressed = bounds(host) { it.tag == "config-preview:LIGHT" }
    check(pressed.width < rest.width * .96f && abs(pressed.center.y - rest.center.y) <= 3f) {
        "Sheet card did not shrink on press: rest=$rest pressed=$pressed"
    }
    capture(folder, "sheet-pressed")
    val midX = onCardX - width * .38f
    touch.drag(onCardX, rest.center.y, midX, rest.center.y, steps = 8)
    frames(host, 3)
    capture(folder, "sheet-mid-swipe")
    // Drag past midpoint, pause to simulate holding past 50%, then capture right before and right after finger release.
    val pastHalfX = onCardX - width * .62f
    touch.drag(midX, rest.center.y, pastHalfX, rest.center.y, steps = 6)
    frames(host, 12)
    capture(folder, "sheet-before-release")
    touch.touch(MotionEvent.ACTION_UP, pastHalfX, rest.center.y)
    frames(host, 2)
    capture(folder, "sheet-after-release")
    verifyEndfieldAndPaletteSync(touch, host, folder, rest, width, dp)
}

private fun AppUiVerification.verifyEndfieldAndPaletteSync(touch: TabRowTouch, host: MainActivity, folder: File,
    rest: Rect, width: Float, dp: Float) {
    framesUntil(host) { node(host) { it.tag == "config-preview:DARK" } != null && find("深色", exact = true) != null }
    frames(host, 45)
    val dark = bounds(host) { it.tag == "config-preview:DARK" }
    check(abs(dark.width - rest.width) <= 2f) { "Sheet card did not rebound after swipe: $dark vs $rest" }
    // Touch outside card (below preview, inside pager): must not shrink the card, still swipes to Endfield.
    val outsideY = dark.bottom + 18 * dp
    touch.touch(MotionEvent.ACTION_DOWN, width * .80f, outsideY)
    frames(host, 12)
    val outsideDown = bounds(host) { it.tag == "config-preview:DARK" }
    check(abs(outsideDown.width - dark.width) <= 1.5f) { "Outside touch shrunk card: $outsideDown vs $dark" }
    touch.drag(width * .80f, outsideY, width * .08f, outsideY, steps = 10)
    touch.touch(MotionEvent.ACTION_UP, width * .08f, outsideY)
    framesUntil(host) { find("明日方舟：终末地数据工具", exact = true) != null }
    frames(host, 40)
    check(node(host) { it.tag == "dropdown:配色" } == null) { "Palette row stayed visible on Endfield page" }
    capture(folder, "sheet-endfield")
    // Swipe back to Arknights Dark, then switch palette to Light via dropdown and verify pager scrolls back.
    touch.touch(MotionEvent.ACTION_DOWN, width * .15f, outsideY)
    touch.drag(width * .15f, outsideY, width * .85f, outsideY, steps = 10)
    touch.touch(MotionEvent.ACTION_UP, width * .85f, outsideY)
    framesUntil(host) { find("明日方舟数据工具", exact = true) != null && node(host) { it.tag == "dropdown:配色" } != null }
    frames(host, 30)
    click("配色")
    frames(host)
    click("浅色")
    framesUntil(host) { node(host) { it.tag == "config-preview:LIGHT" } != null }
    frames(host, 35)
    runOnMainSync { dialog.value = "" }
    frames(host, 40)
}
