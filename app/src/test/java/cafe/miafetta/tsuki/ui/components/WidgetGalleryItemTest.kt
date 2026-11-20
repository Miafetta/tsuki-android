package cafe.miafetta.tsuki.ui.components

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.utils.SinkFeedback

class WidgetGalleryItemTest {
    // The reference phone is 1200 px at 3.25 px/dp, about 369.2dp; HyperOS's preview there is 988 px, about 304dp.
    @Test fun previewTakesTheSystemShareOfThePageOrSheetWidth() {
        val page = 1200 / 3.25f
        // The Widgets page list keeps 12dp at each side; the widget sheet's settings span it (WidgetSheet).
        for (edge in listOf(12f, 0f)) {
            val width = widgetPreviewWidth((page - 2 * edge).dp, edge.dp)
            assertEquals(988 / 3.25f, width.value, 1f)
            assertEquals(page * WidgetPreviewWidthFraction, width.value, .01f)
        }
        // A wide page (the 600dp cap) scales with it; a slot narrower than the share keeps the preview inside it.
        assertEquals(600 * WidgetPreviewWidthFraction, widgetPreviewWidth(576.dp, 12.dp).value, .01f)
        assertEquals(100f, widgetPreviewWidth(100.dp, 100.dp).value, 0f)
    }

    // The sheet opens once the preview has sprung back from Miuix's default sink: within 0.2% of full size, and not
    // earlier than needed (two thirds of the wait in, the preview is still visibly smaller).
    @Test fun sheetOpensOnceTheSinkHasSprungBack() {
        val sink = SinkFeedback()
        val release = TargetBasedAnimation(sink.animationSpec, Float.VectorConverter, sink.sinkAmount, 1f)
        fun scaleAt(millis: Long) = release.getValueFromNanos(millis * 1_000_000)
        assertTrue(abs(1f - scaleAt(OpenAfterReleaseMillis)) < .002f)
        assertTrue(abs(1f - scaleAt(OpenAfterReleaseMillis * 2 / 3)) > .005f)
    }

    // A tab's gallery fades through an offscreen layer cut to its bounds; every preview's shadow must stay inside it.
    @Test fun galleryHoldsThePreviewShadows() {
        assertEquals(14f, PreviewShadowAbove.value, 0f)
        assertEquals(WidgetGalleryTop, PreviewShadowAbove)
        // The last entry ends at least the name gap below its preview; the gallery's bottom covers the rest of the shadow.
        assertTrue(WidgetNameGap + WidgetGalleryBottom >= PreviewShadowBelow)
        // At the sides, the page's list leaves the reference phone's preview its shadow's reach.
        val page = 1200 / 3.25f
        val list = page - 2 * 12f
        val side = (list - widgetPreviewWidth(list.dp, 12.dp).value) / 2
        assertTrue("side room $side", side >= PreviewShadowSide.value)
    }

    // Tab row to first preview: the list's 10dp and the gallery's top, about the 24.5dp HyperOS leaves between entries.
    // Entry to entry: the 12sp name's line box ends 0.21 em below its ink, then the gallery's gap; HyperOS: 24.5dp.
    @Test fun galleryKeepsTheSystemRhythm() {
        assertEquals(24f, (PageListSpacing + WidgetGalleryTop).value, 0f)
        assertEquals(24.5f, WidgetEntryGap.value + 12 * LineBelowInkEm, .3f)
    }
}
