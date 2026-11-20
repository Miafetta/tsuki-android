package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// HyperOS's widget list (2026-11-20, 3.25 px/dp) starts the next preview 24.5dp below a name's ink. The 12sp name's line
// box ends 2.5dp below its ink on the reference phone (LineBelowInkEm), so the entries keep 22dp between them.
internal val WidgetEntryGap = 22.dp

// The gallery holds the first preview's shadow above it and the last one's below it, whatever the name's height. Above,
// that is also the room between the Widgets page's tab row and the first preview: with the list's 10dp, 24dp, about the
// 24.5dp HyperOS leaves between its entries.
internal val WidgetGalleryTop = PreviewShadowAbove
internal val WidgetGalleryBottom = PreviewShadowBelow - WidgetNameGap

// The entries of one Widgets page tab (WidgetGalleryItem), as one list item so that the tab switch fades them as a
// whole (C14). LazyColumn fades an item through an offscreen layer cut to the item's bounds; with one item per entry,
// the shadow above each preview lay outside its item, vanished for the whole fade and came back when it ended.
@Composable
internal fun WidgetGallery(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = WidgetGalleryTop, bottom = WidgetGalleryBottom),
        verticalArrangement = Arrangement.spacedBy(WidgetEntryGap), content = content)
}
