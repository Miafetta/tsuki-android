package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.glass.GlassBackground
import cafe.miafetta.tsuki.ui.glass.GlassSurface
import cafe.miafetta.tsuki.ui.glass.dockSelectionColor
import cafe.miafetta.tsuki.ui.glass.dockShadow

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TsukiNavDock(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val sources = remember { List(3) { MutableInteractionSource() } }
    val keyboardPressed = sources.map { it.collectIsPressedAsState().value }.indexOfLast { it }
    var pointerPosition by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val select by rememberUpdatedState(onSelect)
    val pressed = pointerPosition?.roundToInt() ?: keyboardPressed
    val target = pointerPosition ?: (if (pressed >= 0) pressed else selected).toFloat()
    val motion = animateSlotMotion(target, pressed >= 0, dragging, DockSlots, "dock")
    val selection = dockSelectionColor()
    val foreground = MiuixTheme.colorScheme.onSurface
    val pressedTint = slotPressTint()
    Box(modifier.width(235.dp).height(52.dp)
        .selectableGroup().testTag("nav-dock").pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                down.consume()
                val start = dockPointerPosition(down.position.x, size.width.toFloat()).roundToInt().toFloat()
                pointerPosition = start
                var cancelled = false
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        // A tap near the 52dp dock's edge often drifts a little past it before lifting; only leaving
                        // by more than the touch slop cancels the tap.
                        val slop = viewConfiguration.touchSlop
                        val outside = change.position.x !in -slop..size.width + slop ||
                            change.position.y !in -slop..size.height + slop
                        // Once horizontal dragging starts, retain control outside until release or system cancellation.
                        if (change.isConsumed || (outside && !dragging)) cancelled = true
                        if (!change.pressed) {
                            if (!cancelled) select((pointerPosition ?: start).roundToInt())
                            change.consume()
                            break
                        }
                        if (cancelled) dragging = false
                        else if (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) dragging = true
                        pointerPosition = if (cancelled) null else
                            dragSlotPosition(start, change.position.x - down.position.x, size.width * 73f / 235f, 2f)
                        change.consume()
                    }
                } finally {
                    pointerPosition = null
                    dragging = false
                }
            }
        }) {
        Box(Modifier.matchParentSize().dockShadow())
        GlassBackground(GlassSurface.Dock, Modifier.matchParentSize())
        Box(Modifier.align(Alignment.CenterStart).padding(start = 3.dp)
            .width(83.dp).height(46.dp).graphicsLayer { slotIndicator(motion, 73.dp.toPx(), DockSlots) }
            .testTag("nav-indicator").background(selection, RoundedCornerShape(23.dp))
            .background(foreground.copy(alpha = pressedTint * motion.pressure), RoundedCornerShape(23.dp)))
        DockTabs(selected, pressed, sources, foreground, onSelect, Modifier.align(Alignment.Center))
    }
}

// The dock's slots in dp, as calibrated: 73dp pitch, 83dp indicator, three slots, at most 18dp of drag stretch.
// Other rows scale the stretch from these numbers (TsukiTabRow).
internal val DockSlots = SlotGeometry(73f, 83f, 2f, 18f)

internal fun dockPointerPosition(x: Float, width: Float): Float = slotPosition(x / width * 235f, 44.5f, 73f, 2f)

@Composable
private fun DockTabs(selected: Int, pressed: Int, sources: List<MutableInteractionSource>,
    foreground: androidx.compose.ui.graphics.Color, onSelect: (Int) -> Unit, modifier: Modifier) {
    Row(modifier.padding(horizontal = 8.dp)) {
        listOf("概览" to MiuixIcons.Home, "部件" to MiuixIcons.All, "我的" to MiuixIcons.ContactsCircle)
            .forEachIndexed { index, (title, icon) ->
                val emphasis by animateSlotEmphasis(pressed == index, "dock-content-$index")
                Box(Modifier.width(73.dp).height(52.dp).testTag("nav-tab:$index")
                    .selectable(selected == index, interactionSource = sources[index], indication = null,
                        role = Role.Tab, onClick = { onSelect(index) }), contentAlignment = Alignment.Center) {
                    Column(Modifier.graphicsLayer { slotContent(emphasis) },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, Modifier.size(24.dp), tint = foreground)
                        Text(title, fontSize = 11.sp, lineHeight = 14.sp,
                            fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium,
                            color = foreground, maxLines = 1)
                    }
                }
            }
    }
}
