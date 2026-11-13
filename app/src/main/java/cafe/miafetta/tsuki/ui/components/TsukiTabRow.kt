package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

// The widgets page's contour tab row (C7). At rest it looks like Miuix TabRowWithContour as the page configured it;
// that component exposes neither its indicator nor its gestures, and the row presses and drags like the dock (C3)
// with the same motion (SlotMotion). One difference, because the row sits in a vertically scrolling page: a press that
// first moves vertically past the touch slop drops its preview and leaves the gesture to the list.
// Tags: "$tag-tabs" (row), "$tag-indicator", "$tag-tab:<index>".
@Composable
internal fun TsukiTabRow(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, tag: String,
    modifier: Modifier = Modifier) {
    val sources = remember(tabs.size) { List(tabs.size) { MutableInteractionSource() } }
    val keyboardPressed = sources.map { it.collectIsPressedAsState().value }.indexOfLast { it }
    val tracking = remember { TabTracking() }
    val pressed = tracking.position?.roundToInt() ?: keyboardPressed
    val select by rememberUpdatedState(onSelect)
    val shared = LocalPagerSwipeGuard.current
    val own = remember { PagerSwipeGuard() }
    val guard = shared ?: own
    BoxWithConstraints(modifier.fillMaxWidth().height(TabRowDefaults.TabRowWithContourHeight)) {
        // TabRowWithContour's split: the width inside the contour, less the gaps, in equal parts.
        val tabWidth = (maxWidth - TabContour * 2 - TabGap * (tabs.size - 1)) / tabs.size
        val layout = with(LocalDensity.current) {
            TabLayout(TabContour.roundToPx(), tabWidth.roundToPx(), TabGap.roundToPx(), tabs.size)
        }
        val current by rememberUpdatedState(layout)
        val target = tracking.position ?: (if (pressed >= 0) pressed else selected).toFloat()
        val motion = animateSlotMotion(target, pressed >= 0, tracking.dragging, layout.slots, "$tag-tabs")
        Box(Modifier.fillMaxSize().testTag("$tag-tabs").selectableGroup()
            .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = tabs.size) }
            .pointerInput(guard) {
                awaitEachGesture { trackTabs(tracking, current, guard, feedGuard = shared == null) { select(it) } }
            }
            .squircleBackground(MiuixTheme.colorScheme.surfaceContainer, TabRadius + TabContour)
            .padding(TabContour)) {
            TabIndicator(motion, layout, tabWidth, tag)
            Row(Modifier.fillMaxSize(), Arrangement.spacedBy(TabGap), Alignment.CenterVertically) {
                tabs.forEachIndexed { index, text ->
                    TabItem(text, index, index == selected, index == pressed, tabWidth, sources[index], tag) {
                        onSelect(index)
                    }
                }
            }
        }
    }
}

// TabRowWithContour defaults as the page used them: 5dp contour and gap (not public in Miuix), 17.5dp tab corners.
private val TabContour = 5.dp
private val TabGap = 5.dp
private val TabRadius = 17.5.dp

// Finger x in the row (contour included) to a fractional tab: tab i is centred at contour + tab / 2 + i * (tab + gap).
internal fun tabPointerPosition(x: Float, contour: Float, tab: Float, gap: Float, last: Float): Float =
    slotPosition(x, contour + tab / 2f, tab + gap, last)

// The row's slots in layout px. The dock's motion is calibrated in its own dp (DockSlots); converted by pitch, the
// stretch cap stays the same fraction of a slot (18dp of a 73dp pitch), so the drag stretches over the same lag in
// slots, and the stretch over this indicator's own width gives its scale.
internal fun tabSlots(tab: Int, gap: Int, count: Int): SlotGeometry {
    val pitch = (tab + gap).toFloat()
    return SlotGeometry(pitch, tab.toFloat(), (count - 1).toFloat(), DockSlots.maxStretch / DockSlots.pitch * pitch)
}

// Rounded as the layout rounds padding, widths and spacing, so the indicator at rest sits exactly on its tab.
private data class TabLayout(val contour: Int, val tab: Int, val gap: Int, val count: Int) {
    val pitch = tab + gap
    val slots = tabSlots(tab, gap, count)
    fun position(x: Float) = tabPointerPosition(x, contour.toFloat(), tab.toFloat(), gap.toFloat(), slots.last)
}

// Where a pointer holds the indicator, as a fractional tab, and whether it drags; position is null without a pointer.
@Stable
private class TabTracking {
    var position by mutableStateOf<Float?>(null)
    var dragging by mutableStateOf(false)
}

// One press, handled like TsukiNavDock's except for the vertical hand-off: until the press is known to go sideways,
// moves stay unconsumed for the list, and a vertical decision ends the preview. A sideways drag is consumed by the
// guard ahead of the pager, so that consumption is expected; any other one takes the gesture away as on the dock.
private suspend fun AwaitPointerEventScope.trackTabs(tracking: TabTracking, layout: TabLayout, guard: PagerSwipeGuard,
    feedGuard: Boolean, select: (Int) -> Unit) {
    val down = awaitFirstDown(pass = PointerEventPass.Initial)
    down.consume()
    guard.claim(down.id)
    val start = layout.position(down.position.x).roundToInt().toFloat()
    tracking.position = start
    var cancelled = false
    try {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            val slop = viewConfiguration.touchSlop
            // Outside the pager nothing feeds the guard ahead of this handler.
            if (feedGuard) (change.position - change.previousPosition).let { guard.move(down.id, it.x, it.y, slop) }
            val axis = guard.axis(down.id)
            if (axis == DragAxis.Vertical) break
            val outside = change.position.x !in -slop..size.width + slop ||
                change.position.y !in -slop..size.height + slop
            if ((change.isConsumed && axis != DragAxis.Horizontal) || (outside && !tracking.dragging)) cancelled = true
            if (!change.pressed) {
                if (!cancelled) select((tracking.position ?: start).roundToInt())
                change.consume()
                break
            }
            if (cancelled) tracking.dragging = false
            else if (axis == DragAxis.Horizontal) tracking.dragging = true
            tracking.position = if (cancelled) null else
                dragSlotPosition(start, change.position.x - down.position.x, layout.pitch.toFloat(), layout.slots.last)
            if (tracking.dragging || cancelled) change.consume()
        }
    } finally {
        tracking.position = null
        tracking.dragging = false
        guard.release(down.id)
    }
}

@Composable
private fun TabIndicator(motion: SlotMotionValues, layout: TabLayout, width: Dp, tag: String) {
    val tint = MiuixTheme.colorScheme.onSurface.copy(alpha = slotPressTint())
    Box(Modifier.width(width).fillMaxHeight().graphicsLayer { slotIndicator(motion, layout.pitch.toFloat(), layout.slots) }
        .testTag("$tag-indicator").squircleBackground(MiuixTheme.colorScheme.secondaryContainer, TabRadius)) {
        // The tint fades in its layer: animating the colour would rebuild the squircle brush on every frame.
        Box(Modifier.matchParentSize().graphicsLayer { alpha = motion.pressure }.squircleBackground(tint, TabRadius))
    }
}

// Pointer input belongs to the row; selectable serves keyboard and accessibility selection, without an indication.
@Composable
private fun TabItem(text: String, index: Int, selected: Boolean, pressed: Boolean, width: Dp,
    source: MutableInteractionSource, tag: String, onClick: () -> Unit) {
    val emphasis by animateSlotEmphasis(pressed, "$tag-tab-$index")
    Box(Modifier.width(width).fillMaxHeight().testTag("$tag-tab:$index")
        .selectable(selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
        .semantics { collectionItemInfo = CollectionItemInfo(0, 1, index, 1) }, contentAlignment = Alignment.Center) {
        Text(text, Modifier.graphicsLayer { slotContent(emphasis) },
            color = if (selected) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.body2.fontSize, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
