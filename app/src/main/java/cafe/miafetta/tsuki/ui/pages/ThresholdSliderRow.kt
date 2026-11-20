package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.LabeledSlider
import cafe.miafetta.tsuki.ui.state.ThresholdSlider

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import top.yukonga.miuix.kmp.basic.SliderDefaults

// The threshold on a slider right under its condition menu, without a title of its own: the value being chosen under
// the start of the track and the end of the range under its end (LabeledSlider, UI_DESIGN_CONVENTIONS C15). Dragging
// only moves what is shown ([onDrag]); letting go saves once ([onRelease]). A change while no finger is on the row comes
// from accessibility services, which set the value without ending a drag, so it goes to [onNudge], which saves after a
// pause.
@Composable
internal fun ThresholdSliderRow(slider: ThresholdSlider, value: Int, enabled: Boolean, modifier: Modifier,
    onDrag: (Int) -> Unit, onNudge: (Int) -> Unit, onRelease: () -> Unit) {
    var held by remember { mutableStateOf(false) }
    LabeledSlider(value.toFloat(), { position ->
        val next = slider.valueAt(position)
        when {
            next == value -> Unit
            held -> onDrag(next)
            else -> onNudge(next)
        }
    }, slider.valueLabel(value), slider.endLabel, modifier.watchHeld { held = it }, enabled = enabled,
        valueRange = slider.range.first.toFloat()..slider.range.last.toFloat(), steps = slider.steps,
        onValueChangeFinished = onRelease,
        hapticEffect = if (slider.stepHaptics) SliderDefaults.SliderHapticEffect.Step else SliderDefaults.SliderHapticEffect.Edge)
}

// Reports whether a finger is down anywhere on the row. It reads the events first, before the slider, and consumes
// nothing, so the slider and the page's scrolling behave as without it.
private fun Modifier.watchHeld(onHeld: (Boolean) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            onHeld(event.changes.any { it.pressed })
        }
    }
}
