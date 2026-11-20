package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost

// HyperOS shows a 4×2 widget at 82.3% of the width of the page or sheet it sits on: 988 of 1200 px in the widget list
// and 990 in the add and edit sheets of the reference phone (2026-11-20).
internal const val WidgetPreviewWidthFraction = .823f

// The preview width in a slot [slot] wide whose page or sheet adds [edge] on each side, never wider than the slot.
internal fun widgetPreviewWidth(slot: Dp, edge: Dp): Dp = ((slot + edge * 2) * WidgetPreviewWidthFraction).coerceAtMost(slot)

// The widget preview of the Widgets page and of the widget sheet: the rendered card straight on the background, at the
// width HyperOS gives it and centred. [edge] is the horizontal padding between this slot and the page or sheet edge.
@Composable
internal fun ScaledWidgetPreview(previews: WidgetPreviews, target: PreviewTarget, edge: Dp, modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        WidgetPreviewImage(previews, target.snapshot, target.dark, target.previewId,
            imageModifier.width(widgetPreviewWidth(maxWidth, edge)), target.game)
    }
}
