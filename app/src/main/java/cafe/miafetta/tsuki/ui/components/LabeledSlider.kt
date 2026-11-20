package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Where a LabeledSlider and its labels sit (UI_DESIGN_CONVENTIONS C15). The slider spans BasicComponentDefaults'
// InsideMargin, as in SliderPreference, and starts right below the row it belongs to, whose own bottom margin parts
// them. The start label lines up with the titles of the rows around it, at the start of InsideMargin. The end label
// ends where SliderPreference ends its valueText: SliderPreference puts valueText in a Row with ValueTextEndGap of end
// padding, inside the end area of BasicComponent, which ends at the end of InsideMargin (16dp + 8dp in Miuix 0.9.4).
internal object LabeledSliderDefaults {
    // The end padding of the Row that holds SliderPreference's valueText.
    val ValueTextEndGap = 8.dp

    // Keeps the labels close under the track, so they read as its scale rather than as a row of their own.
    val LabelGap = 4.dp

    fun sliderPadding(direction: LayoutDirection): PaddingValues {
        val margin = BasicComponentDefaults.InsideMargin
        return PaddingValues(start = margin.calculateStartPadding(direction), end = margin.calculateEndPadding(direction))
    }

    fun labelPadding(direction: LayoutDirection): PaddingValues {
        val margin = BasicComponentDefaults.InsideMargin
        return PaddingValues(start = margin.calculateStartPadding(direction), top = LabelGap,
            end = margin.calculateEndPadding(direction) + ValueTextEndGap, bottom = margin.calculateBottomPadding())
    }
}

/**
 * A Miuix [Slider] without a title row, for a row that continues the one above it, with a label under each end:
 * [startLabel] (the value being chosen, say) and [endLabel] (the end of the track), in footnote1 with the colors of a
 * preference summary. SliderPreference is not used: without a title it still keeps the space of its title row and the
 * gap above its slider.
 */
@Composable
internal fun LabeledSlider(value: Float, onValueChange: (Float) -> Unit, startLabel: String, endLabel: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect) {
    val direction = LocalLayoutDirection.current
    val color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSecondaryVariant
    Column(modifier.fillMaxWidth()) {
        Slider(value, onValueChange, Modifier.fillMaxWidth().padding(LabeledSliderDefaults.sliderPadding(direction)),
            enabled = enabled, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished,
            hapticEffect = hapticEffect)
        Row(Modifier.fillMaxWidth().padding(LabeledSliderDefaults.labelPadding(direction))) {
            Text(startLabel, style = MiuixTheme.textStyles.footnote1, color = color)
            Spacer(Modifier.weight(1f))
            Text(endLabel, style = MiuixTheme.textStyles.footnote1, color = color)
        }
    }
}
