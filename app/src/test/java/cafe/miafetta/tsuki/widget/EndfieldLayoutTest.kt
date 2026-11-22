package cafe.miafetta.tsuki.widget

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

// The Endfield card's layouts, aligned to the official Android Skland Endfield widget: the IDs the renderer and sizing
// address, hatched yellow band, 4 realtime rows with emblem watermark, official assets, recovery/count formatting and dark palette.
class EndfieldLayoutTest : WidgetResourceFixture() {
    // children("*") lists every descendant element.
    private fun ids(root: Element): Set<String> =
        (listOf(root) + root.children("*")).mapNotNull { it.attr("id").takeIf(String::isNotEmpty) }.toSet()

    private fun text(path: String) = File(System.getProperty("tsuki.projectDir"), "src/main/$path").readText()

    private val rowNames = listOf("daily", "weekly", "pass", "seek")
    private val rowIds = rowNames.flatMap { name ->
        listOf("cell", "dots", "info", "icon").map { "endfield_${name}_$it" }
    }

    @Test fun nativeCardHasEveryViewTheRendererAndSizingAddress() {
        val content = ids(xml("res/layout/endfield_widget_v3.xml"))
        val frame = listOf("endfield_content", "endfield_band_frame", "endfield_band", "endfield_band_hatch", "endfield_band_marks",
            "endfield_band_word", "endfield_main", "status_header", "status_refresh_hit", "status_refresh_arrow", "endfield_body",
            "status_sanity_primary", "endfield_rows_frame", "endfield_panel_mark", "endfield_rows", "endfield_divider_1",
            "endfield_divider_2", "endfield_divider_3")
        val nativeOnly = listOf("endfield_brand_mark", "endfield_texture", "status_refresh", "status_sanity_icon",
            "endfield_sanity_info", "endfield_sanity_row", "status_sanity", "endfield_sanity_slash", "endfield_sanity_max",
            "endfield_sanity_label", "endfield_recovery_label", "status_sanity_timer_row", "status_sanity_clock", "status_sanity_time") +
            rowNames.flatMap { listOf("endfield_${it}_label", "endfield_$it", "endfield_${it}_slash", "endfield_${it}_max") }
        for (id in frame + rowIds + nativeOnly) assertTrue("missing @+id/$id", "@+id/$id" in content)
        val root = xml("res/layout/endfield_widget_v3.xml")
        assertEquals("@android:id/background", root.attr("id"))
        assertEquals("@drawable/widget_endfield_surface", root.attr("background"))
        assertEquals("true", root.attr("clipToOutline"))
        assertTrue("the card is one layout", root.children("include").isEmpty())
        for (id in listOf("endfield_stripe", "endfield_stripe_magenta", "endfield_stripe_yellow", "endfield_stripe_cyan")) {
            assertTrue("missing @+id/$id", "@+id/$id" in content)
        }
    }

    @Test fun loadingCardKeepsTheFrameIdsAndShowsNoText() {
        val root = xml("res/layout/endfield_widget_loading.xml")
        val ids = ids(root)
        val frame = listOf("endfield_content", "endfield_band_frame", "endfield_band", "endfield_band_hatch", "endfield_band_marks",
            "endfield_band_word", "endfield_main", "status_header", "status_refresh_hit", "status_refresh_arrow", "endfield_body",
            "status_sanity_primary", "endfield_rows_frame", "endfield_panel_mark", "endfield_rows", "endfield_divider_1",
            "endfield_divider_2", "endfield_divider_3", "endfield_stripe")
        val loadingBars = rowNames.flatMap { listOf("endfield_loading_${it}_label", "endfield_loading_$it") }
        for (id in frame + rowIds + loadingBars) assertTrue("loading misses @+id/$id", "@+id/$id" in ids)
        // The header marks are an image; every value is a placeholder bar, so the loading card has no text.
        assertTrue(root.children("TextView").isEmpty())
        assertEquals("@string/endfield_loading", root.attr("contentDescription"))
    }

    @Test fun everyEndfieldResourceIsDarkAndThePreviewShowsTheOfficialElements() {
        for (layout in listOf("endfield_widget_v3", "endfield_widget_loading", "endfield_widget_preview")) {
            val source = text("res/layout/$layout.xml")
            assertFalse("$layout uses a light resource", Regex("_light\"").containsMatchIn(source))
            for (drawable in listOf("band_shape", "band_hatch", "band_marks_v2", "band_word_v2", "brand_v2", "refresh_v3", "bg_v3",
                "panel_mark_v2", "item_dot", "sanity_v2", "daily_v2", "weekly_v2", "pass_v2", "seek")) {
                assertTrue("$layout misses $drawable", source.contains("@drawable/widget_endfield_$drawable"))
            }
        }
        val preview = text("res/layout/endfield_widget_preview.xml")
        val names = listOf("endfield_preview_sanity", "endfield_preview_sanity_max", "endfield_label_sanity",
            "endfield_label_recovery", "endfield_preview_sanity_time", "endfield_label_daily", "endfield_label_weekly",
            "endfield_label_pass", "endfield_label_seek", "endfield_preview_pass", "endfield_preview_seek", "endfield_preview_seek_max")
        for (name in names) assertTrue("preview misses $name", preview.contains("@string/$name"))
        for (color in listOf("widget_endfield_magenta", "widget_endfield_yellow", "widget_endfield_cyan")) {
            assertTrue("preview misses the stripe colour $color", preview.contains("@color/$color"))
        }
    }

    @Test fun officialRecoveryFormatAndCompactCountsMatchAndroidSklandWidget() {
        assertEquals("1min", endfieldRecoveryText(1L))
        assertEquals("1min", endfieldRecoveryText(60_000L))
        assertEquals("45min", endfieldRecoveryText(45 * 60_000L))
        assertEquals("1h 0min", endfieldRecoveryText(60 * 60_000L))
        assertEquals("5h 40min", endfieldRecoveryText((5 * 60 + 40) * 60_000L))
        assertEquals("12h 23min", endfieldRecoveryText((12 * 60 + 23) * 60_000L))
        assertEquals("0", compactEndfieldCount(0))
        assertEquals("100", compactEndfieldCount(100))
        assertEquals("9999", compactEndfieldCount(9999))
        assertEquals("1w", compactEndfieldCount(10_000))
        assertEquals("1.5w", compactEndfieldCount(15_000))
        assertEquals("12.5w", compactEndfieldCount(125_000))
        assertEquals("19.9w", compactEndfieldCount(199_999))
        assertEquals("20w", compactEndfieldCount(200_000))
    }
}
