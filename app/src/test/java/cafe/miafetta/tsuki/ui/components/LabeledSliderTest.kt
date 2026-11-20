package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

// Where a LabeledSlider's labels sit: the start label at the start of BasicComponent's 16dp margin, with the row titles;
// the end label 8dp further in, where SliderPreference's valueText ends; the slider across the margin, as in
// SliderPreference, right below the row above it.
class LabeledSliderTest {
    @Test fun labelsSitWhereThePreferenceTextsSit() {
        for (direction in LayoutDirection.entries) {
            val labels = LabeledSliderDefaults.labelPadding(direction)
            assertEquals(16.dp, labels.calculateStartPadding(direction))
            assertEquals(24.dp, labels.calculateEndPadding(direction))
            assertEquals(4.dp, labels.calculateTopPadding())
            assertEquals(16.dp, labels.calculateBottomPadding())
            val slider = LabeledSliderDefaults.sliderPadding(direction)
            assertEquals(16.dp, slider.calculateStartPadding(direction))
            assertEquals(16.dp, slider.calculateEndPadding(direction))
            assertEquals(0.dp, slider.calculateTopPadding())
        }
    }
}
