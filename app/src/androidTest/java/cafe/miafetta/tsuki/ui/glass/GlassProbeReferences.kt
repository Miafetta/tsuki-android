package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.components.BackButtonPadding
import cafe.miafetta.tsuki.ui.components.TitleMotion
import cafe.miafetta.tsuki.ui.components.TopBarContentGap
import cafe.miafetta.tsuki.ui.pages.SecondaryScaffold
import cafe.miafetta.tsuki.ui.semanticsNodes
import cafe.miafetta.tsuki.ui.shell
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as LayoutRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// Rows below the unselected chip, in dp, where no shadow may show.
private val ChipProbeDp = listOf(1, 3, 6, 10)

// Screen bounds of the probe chip tagged game-chip:[id]; the probe's Compose root fills the window.
internal fun GlassProbe.chipBounds(id: String): Rect {
    var bounds = Rect()
    runner.runOnMainSync {
        val node = host.semanticsNodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "game-chip:$id" }
        val box = node.boundsInRoot
        bounds = Rect(box.left.roundToInt(), box.top.roundToInt(), box.right.roundToInt(), box.bottom.roundToInt())
    }
    return bounds
}

// Where the chip's dropShadow can draw: its bitmap ends ChipShadowRadius outside the chip (one pixel more for rounding).
internal fun GlassProbe.chipShadowBox(chip: Rect): Rect {
    val reach = ceil(ChipShadowRadius.value * density).toInt() + 1
    return Rect(chip.left - reach, chip.top - reach, chip.right + reach, chip.bottom + reach)
}

// The selected chip's shadow at rest and with blur off, where the bare stripes show around the chips (with the glass on,
// the header blurs them). Each row or column out from the chip is compared with the same pixels at sourceY: the stripes
// are vertical, so any difference is drawn over them. Above and below: every pixel row from 2 px out (the first row holds
// the chip's antialiased edge) to ChipShadowRadius + 2dp, over the chip's straight part; beside the ends: every column
// over the chip's middle third, up to the screen edge or the next chip. All four profiles go to chip-shadow-<palette>.txt.
// Checked: at rest, still a shadow 12dp above and below and no step over 1.5 levels between neighbouring rows (a cut at
// the row's bounds was 3.8); with blur off the same below (from 12dp above the chip the opaque header fade covers the
// stripes); and no shadow below the unselected chip.
internal fun GlassProbe.chipShadows(name: String, rest: Bitmap, blurOff: Bitmap) {
    val selected = chipBounds("selected")
    val plain = chipBounds("plain")
    val plainBand = Rect(plain.left + plain.height() / 2, plain.top, plain.right - plain.height() / 2, plain.bottom)
    expect(!Rect.intersects(chipShadowBox(selected), plainBand)) { "$name: probe chips too close to tell their shadows apart" }
    val reach = ((ChipShadowRadius.value + 2) * density).roundToInt()
    val at12 = (12 * density).roundToInt()
    val lines = mutableListOf("density $density px/dp, selected chip $selected, unselected chip $plain, rows from 2 px out")
    for ((state, shot) in listOf("rest" to rest, "blur-off" to blurOff)) {
        val above = (2..reach).map { it to rowShift(shot, selected, selected.top - it) }
        val below = (2..reach).map { it to rowShift(shot, selected, selected.bottom - 1 + it) }
        val left = (2..reach).takeWhile { selected.left - it >= 0 }.map { it to columnShift(shot, selected, selected.left - it) }
        val right = (2..reach).takeWhile { selected.right - 1 + it < plain.left }
            .map { it to columnShift(shot, selected, selected.right - 1 + it) }
        val clear = ChipProbeDp.map { it to rowShift(shot, plain, plain.bottom + (it * density).roundToInt()) }
        lines += "$name $state above: ${above.text()}"
        lines += "$name $state below: ${below.text()}"
        lines += "$name $state left: ${left.text()}"
        lines += "$name $state right (crossed by the large title's ink): ${right.text()}"
        lines += "$name $state below the unselected chip, dp: ${clear.text()}"
        val sides = if (state == "rest") listOf("above" to above, "below" to below) else listOf("below" to below)
        for ((side, profile) in sides) {
            val far = profile.firstOrNull { it.first == at12 }?.second ?: 0f
            expect(far <= -.3f) { "$name $state: no shadow 12dp $side the selected chip ($far)" }
            val step = profile.zipWithNext().maxOf { (a, b) -> abs(a.second - b.second) }
            expect(step <= 1.5f) { "$name $state: the shadow $side the selected chip has a step of $step levels" }
        }
        expect(clear.all { abs(it.second) <= 1f }) { "$name $state: a shadow below the unselected chip: $clear" }
    }
    File(folder, "chip-shadow-$name.txt").writeText(lines.joinToString("\n", postfix = "\n"))
}

private fun List<Pair<Int, Float>>.text() = joinToString(" ") { "${it.first}:${"%.2f".format(Locale.ROOT, it.second)}" }

// Mean luma difference of row [y] from sourceY over [chip]'s straight part.
private fun GlassProbe.rowShift(shot: Bitmap, chip: Rect, y: Int): Float =
    (chip.left + chip.height() / 2 until chip.right - chip.height() / 2)
        .map { luma(shot.getPixel(it, y)) - luma(shot.getPixel(it, sourceY)) }.average().toFloat()

// Mean luma difference of column [x] from the same column at sourceY over [chip]'s middle third.
private fun GlassProbe.columnShift(shot: Bitmap, chip: Rect, x: Int): Float =
    (chip.top + chip.height() / 3 until chip.bottom - chip.height() / 3)
        .map { luma(shot.getPixel(x, it)) - luma(shot.getPixel(x, sourceY)) }.average().toFloat()

private fun luma(pixel: Int): Int = ((pixel shr 16 and 255) * 299 + (pixel shr 8 and 255) * 587 + (pixel and 255) * 114) / 1000

// The system Settings page "移动网络" (recorded 2026-11-15 and 2026-11-16, 1200 x 2670 px, 3.25 px/dp), expanded at rest.
private const val ReferenceTitle = "移动网络"
private const val ReferenceCard = "title-reference-card"
private const val SystemReference = "system 移动网络, expanded at rest: large title ink rows 354..445, columns 90..471; " +
    "back button ink rows 193..256, columns 86..120; first card straight top edge row 509 (status bar 140 px)"

// The real SecondaryScaffold beside that page: the same title, its back button and one card with the product pages' 12dp
// side padding, light, at the device's own density (not the probe's 360dp one), expanded at rest, then collapsed by a
// swipe. The layout bounds, the page-to-card row and the measured ink go alongside; nothing here is checked.
internal fun GlassProbe.titleReference() {
    runner.runOnMainSync {
        (host as ComponentActivity).setContent {
            TsukiAppTheme("LIGHT") {
                SecondaryScaffold(ReferenceTitle, blurEnabled = true, active = true, onBack = {},
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Card(Modifier.fillMaxWidth().testTag(ReferenceCard)) {
                        BasicComponent(title = "移动数据")
                        BasicComponent(title = "SIM 卡信息与设置")
                    }
                }
            }
        }
    }
    settle()
    val layout = referenceLayout()
    val density = host.resources.displayMetrics.density
    val status = statusBarTop()
    val expandedBottom = (layout.largeTitle.bottom + TopAppBarDefaults.LargeTitleBottomPadding.value * density).roundToInt()
    val lines = mutableListOf(titleGeometry(), SystemReference, layout.describe(status, density, expandedBottom))
    val expanded = capture("title-reference-expanded")
    lines += "expanded: ${cardEdge(expanded, layout.card)}; bar bottom (clip edge) $expandedBottom px; " +
        titleInk(expanded, expandedBottom, true)
    expanded.recycle()
    val decor = host.window.decorView
    val y = decor.height * 3 / 4
    runner.uiAutomation.shell("input swipe ${decor.width / 2} $y ${decor.width / 2} ${y - decor.height / 3} 600")
    settle()
    settle()
    val collapsedBottom = status + ((TopAppBarDefaults.CollapsedHeight + TopAppBarDefaults.LargeTitleBottomPadding).value *
        density).roundToInt()
    val collapsed = capture("title-reference-collapsed")
    lines += "collapsed: bar bottom (clip edge) $collapsedBottom px; " + titleInk(collapsed, collapsedBottom, false)
    collapsed.recycle()
    File(folder, "title-reference-geometry.txt").writeText(lines.joinToString("\n", postfix = "\n"))
}

private fun GlassProbe.statusBarTop() =
    host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top

// Window bounds of the card, the back icon, its clickable button and Miuix's own (transparent) large title line box.
private class ReferenceLayout(val card: LayoutRect, val icon: LayoutRect, val button: LayoutRect, val largeTitle: LayoutRect)

private fun GlassProbe.referenceLayout(): ReferenceLayout {
    var layout: ReferenceLayout? = null
    runner.runOnMainSync {
        val nodes = host.semanticsNodes()
        val card = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == ReferenceCard }
        val icon = nodes.first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("返回") == true }
        val button = generateSequence(icon) { it.parent }.first { it.config.getOrNull(SemanticsActions.OnClick) != null }
        // Miuix keeps two transparent titles with the text: the large one has the taller line box.
        val title = nodes.filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == ReferenceTitle } == true }
            .maxBy { it.boundsInWindow.height }
        layout = ReferenceLayout(card.boundsInWindow, icon.boundsInWindow, button.boundsInWindow, title.boundsInWindow)
    }
    return checkNotNull(layout)
}

private fun ReferenceLayout.describe(status: Int, density: Float, barBottom: Int): String {
    fun dp(px: Float) = "%.1f".format(Locale.ROOT, px / density)
    val top = card.top.roundToInt()
    return "layout (window px, inclusive top, exclusive bottom): first content (card) top $top, ${dp(top - status.toFloat())}dp " +
        "below the status bar, ${top - barBottom} px (${dp((top - barBottom).toFloat())}dp) below the expanded bar; " +
        "Miuix large title line box ${largeTitle.text()}; back icon ${icon.text()}; back button (clickable, Compose expands " +
        "its touch target to 48dp) ${button.text()}; start padding $BackButtonPadding, content gap $TopBarContentGap"
}

private fun LayoutRect.text() = "rows ${top.roundToInt()}..${bottom.roundToInt()}, columns ${left.roundToInt()}..${right.roundToInt()}"

// Down the card's middle column: the page's luma just below the status bar, the first row that differs from it by 2 levels
// or more, and the luma of the rows around the card's layout top.
private fun GlassProbe.cardEdge(shot: Bitmap, card: LayoutRect): String {
    val status = statusBarTop()
    val x = card.center.x.roundToInt()
    val page = luma(shot.getPixel(x, status + 2))
    val edge = (status + 2 until card.bottom.roundToInt().coerceAtMost(shot.height))
        .firstOrNull { abs(luma(shot.getPixel(x, it)) - page) >= 2 }
    val top = card.top.roundToInt()
    val around = (top - 3..top + 2).filter { it in 0 until shot.height }.joinToString(" ") { "$it:${luma(shot.getPixel(x, it))}" }
    return "page luma $page at column $x, page-to-card row ${edge ?: "none"}, rows $around"
}

private fun GlassProbe.titleGeometry(): String {
    val metrics = host.resources.displayMetrics
    val collapsed = TopAppBarDefaults.CollapsedHeight.value * metrics.density
    val drop = (TitleMotion.LargeTitleDropDp * metrics.density).roundToInt()
    return "density ${metrics.density} px/dp, font scale ${host.resources.configuration.fontScale}, screen " +
        "${metrics.widthPixels} x ${metrics.heightPixels} px, status bar ${statusBarTop()} px, collapsed bar " +
        "${collapsed.roundToInt()} px, large title drop ${TitleMotion.LargeTitleDropDp}dp = $drop px (the drawn large title " +
        "line box is the Miuix one below moved down by it)"
}

// Ink (> 90 levels from the page) of the back button and of the large (expanded) or small (collapsed) title.
private fun GlassProbe.titleInk(shot: Bitmap, barBottom: Int, expanded: Boolean): String {
    val status = statusBarTop()
    val collapsed = (TopAppBarDefaults.CollapsedHeight.value * host.resources.displayMetrics.density).roundToInt()
    val page = luma(shot.getPixel(shot.width / 2, shot.height * 3 / 4))
    val back = inkBox(shot, page, 0, shot.width / 4, status, status + collapsed)
    val title = if (expanded) inkBox(shot, page, 0, shot.width, status + collapsed, barBottom)
        else inkBox(shot, page, shot.width / 4, shot.width * 3 / 4, status, status + collapsed)
    val gap = title?.let { " (${barBottom - it.bottom - 1} clear rows between the ink and the clip edge)" }.orEmpty()
    return "page $page; back button ink ${back.describe()}; ${if (expanded) "large" else "small"} title ink ${title.describe()}$gap"
}

private fun inkBox(shot: Bitmap, page: Int, x0: Int, x1: Int, y0: Int, y1: Int): Rect? {
    var box: Rect? = null
    for (y in y0 until y1.coerceAtMost(shot.height)) for (x in x0 until x1) {
        // Inclusive bounds: Rect.union(x, y) moves right and bottom onto the point itself.
        if (abs(luma(shot.getPixel(x, y)) - page) > 90) box = (box ?: Rect(x, y, x, y)).apply { union(x, y) }
    }
    return box
}

// Inclusive rows and columns, as the system reference is written.
private fun Rect?.describe(): String = this?.let { "rows $top..$bottom, columns $left..$right (${bottom - top + 1} px high)" }
    ?: "none"
