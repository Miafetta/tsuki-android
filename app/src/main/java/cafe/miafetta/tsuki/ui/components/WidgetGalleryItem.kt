package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

// Measured on HyperOS's widget list (2026-11-20): the name's ink starts 7.7dp below the preview. The name's line box
// starts 2.3dp above its ink (LineAboveInkEm at 12sp), so Tsuki's ink sits 7.2dp below the preview on the reference
// phone. The space between entries is the gallery's (WidgetGallery).
internal val WidgetNameGap = 5.dp

// SinkFeedback's default spring (damping ratio 0.8, stiffness 600) is back within 0.2% of full size 150 ms after release;
// the sheet opens then, so the preview is seen to spring back before it opens, as on HyperOS.
internal const val OpenAfterReleaseMillis = 150L

// One widget on the Widgets page, laid out like HyperOS's widget list: the preview straight on the page, the widget's name
// centred below it in bold and, when given, a status line (why the last refresh failed) in the error colour. The whole
// entry takes the press, as Miuix's Card wires it (pressable without delay, then clickable), but only the preview sinks
// with Miuix's SinkFeedback: the text stays put, as in HyperOS's list. [onClick] runs once the preview has sprung back.
// [edge] is the list's horizontal padding (see ScaledWidgetPreview).
@Composable
internal fun WidgetGalleryItem(previews: WidgetPreviews, target: PreviewTarget, name: String, edge: Dp, onClick: () -> Unit,
    modifier: Modifier = Modifier, status: String = "") {
    val interactions = remember { MutableInteractionSource() }
    val sink = remember { SinkFeedback() }
    val scope = rememberCoroutineScope()
    val opening = remember { arrayOfNulls<Job>(1) }
    val currentOnClick by rememberUpdatedState(onClick)
    Column(modifier.testTag("widget-preview:${target.previewId}").fillMaxWidth()
        .pressable(interactions, indication = null, delay = null)
        .clickable(interactions, indication = null, role = Role.Button) {
            // Repeated taps while the preview springs back open the sheet once.
            if (opening[0]?.isActive != true) opening[0] = scope.launch {
                delay(OpenAfterReleaseMillis)
                currentOnClick()
            }
        }, horizontalAlignment = Alignment.CenterHorizontally) {
        // The tag sits inside the sink, so the image's bounds follow it.
        ScaledWidgetPreview(previews, target, edge,
            imageModifier = Modifier.indication(interactions, sink).testTag("widget-image:${target.previewId}"))
        Text(name, Modifier.padding(top = WidgetNameGap), color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (status.isNotEmpty()) Text(status, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}
