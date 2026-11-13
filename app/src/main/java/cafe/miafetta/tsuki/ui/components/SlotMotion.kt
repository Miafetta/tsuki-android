package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import kotlin.math.abs

// Press and drag motion of a selection indicator over a row of equal slots. The dock (C3) and the widgets tab row (C7)
// share it so the two cannot drift apart. Fitted frame by frame to HyperOS system recordings on the dock; lengths live
// in each row's SlotGeometry, see docs/UI_DESIGN_CONVENTIONS.md C3.
internal object SlotMotion {
    // shortcut: public Compose easing fitted to frame-timed system recordings; recalibrate if system motion changes.
    val Easing = CubicBezierEasing(.2f, .8f, .2f, 1f)
    // The indicator follows a dragging finger, otherwise it settles on its target slot.
    const val DragMillis = 100
    const val SettleMillis = 220
    // Pressing in, and letting go (deformation, tint, content emphasis, stretch release).
    const val PressMillis = 100
    const val ReleaseMillis = 200
    // A pressed indicator gets this much narrower and flatter; pressed slot content this much fainter and smaller.
    const val PressShrinkX = .07f
    const val PressShrinkY = .13f
    const val ContentFade = .38f
    const val ContentShrink = .07f
    // onSurface over the pressed indicator.
    const val TintLight = .10f
    const val TintDark = .16f
}

// A row's slots in one length unit: [pitch] between slot centres, [indicator] width, [last] slot index and the longest
// drag [maxStretch]. The dock states its calibrated layout in dp (DockSlots); the tab row its measured layout in px.
internal data class SlotGeometry(val pitch: Float, val indicator: Float, val last: Float, val maxStretch: Float)

// Fractional slot under [x], with the first slot centred at [firstCenter], all in one unit.
internal fun slotPosition(x: Float, firstCenter: Float, pitch: Float, last: Float): Float =
    ((x - firstCenter) / pitch).coerceIn(0f, last)

// The pressed slot moved by the finger's travel; [pitchPx] is the slot pitch in the same px as [dx].
internal fun dragSlotPosition(start: Float, dx: Float, pitchPx: Float, last: Float): Float =
    (start + dx / pitchPx).coerceIn(0f, last)

// While dragging, the indicator stretches by its lag behind the finger, up to the cap. The stretch spreads to both
// sides, so it stays below twice the distance to the nearer end of the travel and never leaves the row.
internal fun slotStretch(lag: Float, position: Float, slots: SlotGeometry): Float = with(slots) {
    minOf(lag * pitch, maxStretch, position * (pitch * 2), (last - position) * (pitch * 2)).coerceAtLeast(0f)
}

// Added horizontal scale; the animated stretch trails the position, so the end limit is applied again where it is now.
internal fun stretchScale(stretch: Float, position: Float, slots: SlotGeometry): Float = with(slots) {
    minOf(stretch, position * (pitch * 2), (last - position) * (pitch * 2)).coerceAtLeast(0f) / indicator
}

internal fun slotPressSpec(pressed: Boolean): TweenSpec<Float> =
    tween(if (pressed) SlotMotion.PressMillis else SlotMotion.ReleaseMillis, easing = SlotMotion.Easing)

@Stable
internal class SlotMotionValues(private val positionState: State<Float>, private val pressureState: State<Float>,
    private val stretchState: State<Float>) {
    val position: Float get() = positionState.value
    val pressure: Float get() = pressureState.value
    val stretch: Float get() = stretchState.value
}

// The indicator's position follows [target], its pressure the press, its stretch the lag while dragging.
@Composable
internal fun animateSlotMotion(target: Float, pressed: Boolean, dragging: Boolean, slots: SlotGeometry,
    label: String): SlotMotionValues {
    val position = animateFloatAsState(target, tween(if (dragging) SlotMotion.DragMillis else SlotMotion.SettleMillis,
        easing = SlotMotion.Easing), label = "$label-position")
    val pressure = animateFloatAsState(if (pressed) 1f else 0f, slotPressSpec(pressed), label = "$label-pressure")
    val stretch = animateFloatAsState(if (dragging) slotStretch(abs(target - position.value), position.value, slots) else 0f,
        tween(if (dragging) SlotMotion.DragMillis else SlotMotion.ReleaseMillis, easing = SlotMotion.Easing),
        label = "$label-stretch")
    return remember(position, pressure, stretch) { SlotMotionValues(position, pressure, stretch) }
}

@Composable
internal fun animateSlotEmphasis(pressed: Boolean, label: String): State<Float> =
    animateFloatAsState(if (pressed) 1f else 0f, slotPressSpec(pressed), label = label)

@Composable
internal fun slotPressTint(): Float = if (isTsukiDarkTheme()) SlotMotion.TintDark else SlotMotion.TintLight

// Moves the indicator by whole pitches and deforms it; transforms only, so layout and hit areas keep their size.
internal fun GraphicsLayerScope.slotIndicator(motion: SlotMotionValues, pitchPx: Float, slots: SlotGeometry) {
    translationX = motion.position * pitchPx
    scaleX = 1f - SlotMotion.PressShrinkX * motion.pressure + stretchScale(motion.stretch, motion.position, slots)
    scaleY = 1f - SlotMotion.PressShrinkY * motion.pressure
}

internal fun GraphicsLayerScope.slotContent(emphasis: Float) {
    alpha = 1f - SlotMotion.ContentFade * emphasis
    scaleX = 1f - SlotMotion.ContentShrink * emphasis
    scaleY = scaleX
}
