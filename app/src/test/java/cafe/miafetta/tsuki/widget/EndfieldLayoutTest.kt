package cafe.miafetta.tsuki.widget

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

// The Endfield card's layouts, after the official widget: the IDs the renderer and sizing address, the band, panels and
// stripe, the shared refresh hit area, and one dark palette.
class EndfieldLayoutTest : WidgetResourceFixture() {
    // children("*") lists every descendant element.
    private fun ids(root: Element): Set<String> =
        (listOf(root) + root.children("*")).mapNotNull { it.attr("id").takeIf(String::isNotEmpty) }.toSet()

    private fun text(path: String) = File(System.getProperty("tsuki.projectDir"), "src/main/$path").readText()

    private val rowIds = listOf("daily", "weekly", "pass").flatMap { name ->
        listOf("cell", "dots", "info", "icon", "progress").map { "endfield_${name}_$it" }
    }

    @Test fun nativeCardHasEveryViewTheRendererAndSizingAddress() {
        val content = ids(xml("res/layout/endfield_widget.xml"))
        val frame = listOf("endfield_content", "endfield_band", "endfield_main", "status_header", "status_refresh_hit",
            "status_refresh_arrow", "endfield_body", "status_sanity_primary", "endfield_rows", "endfield_divider_1",
            "endfield_divider_2")
        val nativeOnly = listOf("endfield_brand_platform", "endfield_brand_divider", "endfield_brand_game",
            "status_refresh", "status_sanity_icon", "endfield_sanity_info", "status_sanity", "endfield_sanity_label",
            "endfield_recovery_label", "status_sanity_timer_row", "status_sanity_clock", "status_sanity_time") +
            listOf("daily", "weekly", "pass").flatMap { listOf("endfield_${it}_label", "endfield_$it", "endfield_${it}_fill") }
        for (id in frame + rowIds + nativeOnly) assertTrue("missing @+id/$id", "@+id/$id" in content)
        val root = xml("res/layout/endfield_widget.xml")
        assertEquals("@android:id/background", root.attr("id"))
        assertEquals("@drawable/widget_endfield_surface", root.attr("background"))
        assertTrue("the card is one layout", root.children("include").isEmpty())
        for (id in listOf("endfield_stripe", "endfield_stripe_magenta", "endfield_stripe_yellow", "endfield_stripe_cyan")) {
            assertTrue("missing @+id/$id", "@+id/$id" in content)
        }
    }

    @Test fun loadingCardKeepsTheFrameIdsAndShowsNoText() {
        val root = xml("res/layout/endfield_widget_loading.xml")
        val ids = ids(root)
        val frame = listOf("endfield_content", "endfield_band", "endfield_main", "status_header", "status_refresh_hit",
            "status_refresh_arrow", "endfield_body", "status_sanity_primary", "endfield_rows", "endfield_divider_1",
            "endfield_divider_2", "endfield_stripe")
        for (id in frame + rowIds) assertTrue("loading misses @+id/$id", "@+id/$id" in ids)
        // Only the static header marks are text, as on the official card; every value is a placeholder bar.
        val texts = root.children("TextView").map { it.attr("text") }
        assertEquals(listOf("@string/endfield_brand_platform", "@string/endfield_brand_game"), texts)
        assertEquals("@string/endfield_loading", root.attr("contentDescription"))
    }

    @Test fun everyEndfieldResourceIsDarkAndThePreviewShowsTheOfficialElements() {
        for (layout in listOf("endfield_widget", "endfield_widget_loading", "endfield_widget_preview")) {
            val source = text("res/layout/$layout.xml")
            assertFalse("$layout uses a light resource", Regex("_light\"").containsMatchIn(source))
            assertTrue("$layout misses the band", source.contains("@drawable/widget_endfield_band"))
        }
        val preview = text("res/layout/endfield_widget_preview.xml")
        val names = listOf("endfield_brand_platform", "endfield_brand_game", "endfield_preview_sanity", "endfield_label_sanity",
            "endfield_label_recovery", "endfield_preview_sanity_time", "endfield_label_daily", "endfield_label_weekly",
            "endfield_label_pass", "endfield_preview_pass")
        for (name in names) assertTrue("preview misses $name", preview.contains("@string/$name"))
        for (color in listOf("widget_endfield_magenta", "widget_endfield_yellow", "widget_endfield_cyan")) {
            assertTrue("preview misses the stripe colour $color", preview.contains("@color/$color"))
        }
    }
}
