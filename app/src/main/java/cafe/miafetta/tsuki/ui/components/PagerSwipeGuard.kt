package cafe.miafetta.tsuki.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

internal enum class DragAxis { Horizontal, Vertical }

// Which way a press has started to move, by the main pager's own test (Miuix horizontalPagerSwipeOverride): sideways
// once |x| passes the touch slop and outweighs |y|, vertical once |y| passes it first, null while inside the slop.
internal fun dragAxis(x: Float, y: Float, slop: Float): DragAxis? = when {
    abs(x) > slop && abs(x) > abs(y) -> DragAxis.Horizontal
    abs(y) > slop -> DragAxis.Vertical
    else -> null
}

// The main pager takes horizontal drags in the Initial pass, before any page content sees them, so a control in a page
// cannot keep a sideways drag by consuming it itself. Such a control (TsukiTabRow) claims its pointer on press. This
// guard sits on the pager ahead of the swipe override, sums the claimed pointer's movement exactly as the override does
// and consumes it from the event on which the override would start paging: the override then yields and settles the
// page where it is. A vertical decision consumes nothing, so list scrolling, the collapsing top bar and the override's
// own vertical yield behave as if the control were not there.
internal class PagerSwipeGuard {
    private class Claim {
        var x = 0f
        var y = 0f
        var axis: DragAxis? = null
    }

    private val claims = HashMap<PointerId, Claim>()

    fun claim(id: PointerId) {
        claims[id] = Claim()
    }

    fun release(id: PointerId) {
        claims.remove(id)
    }

    fun axis(id: PointerId): DragAxis? = claims[id]?.axis

    // Adds one event's movement of a claimed pointer; true when the event must be consumed ahead of the pager.
    fun move(id: PointerId, dx: Float, dy: Float, slop: Float): Boolean {
        val claim = claims[id] ?: return false
        if (claim.axis == null) {
            claim.x += dx
            claim.y += dy
            claim.axis = dragAxis(claim.x, claim.y, slop)
        }
        return claim.axis == DragAxis.Horizontal
    }
}

// Provided around the main pager's pages together with [pagerSwipeGuard]; null outside the pager.
internal val LocalPagerSwipeGuard = staticCompositionLocalOf<PagerSwipeGuard?> { null }

// Goes before pagerGestureOverride in the pager's modifier chain, so it sees each event first.
internal fun Modifier.pagerSwipeGuard(guard: PagerSwipeGuard): Modifier = this.pointerInput(guard) {
    awaitPointerEventScope {
        while (true) {
            for (change in awaitPointerEvent(PointerEventPass.Initial).changes) {
                val delta = change.position - change.previousPosition
                if (guard.move(change.id, delta.x, delta.y, viewConfiguration.touchSlop)) change.consume()
            }
        }
    }
}
