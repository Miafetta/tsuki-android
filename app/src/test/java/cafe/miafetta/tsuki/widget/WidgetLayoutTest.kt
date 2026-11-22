package cafe.miafetta.tsuki.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

// Widget layouts and palettes: reference structure, supported RemoteViews containers, night mapping and contrast.
class WidgetLayoutTest : WidgetResourceFixture() {
    @Test fun statusMetricsMatchReferenceStructureWithoutAddedFooterAndKeepFixedFourByTwo() {
        val layout = xml("res/layout/status_widget_content.xml")
        val excluded = listOf("@+id/status_title", "@+id/status_refresh", "@+id/status_refresh_arrow")
        val metrics = layout.children("TextView").filter { !it.attr("id").endsWith("_label") && it.attr("id") !in excluded }
        assertEquals(15, metrics.size)
        assertFalse(layout.children("TextView").any { it.attr("id") == "@+id/status_freshness" })
        for (metric in metrics) {
            assertEquals("", metric.attr("ellipsize"))
        }
        val style = xml("res/values/styles.xml").children("style").single { it.getAttribute("name") == "WidgetSummary" }
        val items = style.children("item").associate { it.getAttribute("name") to it.textContent }
        assertFalse(items.containsKey("android:ellipsize"))
        assertFalse(items.containsKey("android:autoSizeTextType"))
        assertEquals("match_parent", items["android:layout_height"])
        assertEquals("@dimen/miuix_widget_body2", items["android:textSize"])
        val modern = xml("res/xml/status_widget_info.xml")
        assertEquals("4", modern.attr("targetCellWidth"))
        assertEquals("2", modern.attr("targetCellHeight"))
        for (path in listOf("res/xml/status_widget_info.xml")) {
            assertEquals("none", xml(path).attr("resizeMode"))
            assertEquals("110dp", xml(path).attr("minHeight"))
        }
        assertEquals(10, layout.children("ImageView").size)
        val refreshIcon = layout.children("ImageView").single { it.attr("id") == "@+id/status_refresh_arrow" }
        assertEquals("12dp", refreshIcon.attr("layout_width"))
        assertEquals("12dp", refreshIcon.attr("layout_height"))
        assertEquals("center_vertical", refreshIcon.attr("layout_gravity"))
        val header = layout.children("LinearLayout").single { it.attr("id") == "@+id/status_header" }
        assertEquals("19.44dp", header.attr("layout_height"))
        val summary = layout.children("GridLayout").single { it.attr("id") == "@+id/status_summary" }
        assertEquals("4", summary.attr("columnCount"))
        assertEquals("2", summary.attr("rowCount"))
        assertEquals("46.08dp", summary.attr("layout_height"))
        val cells = summary.children("LinearLayout").filter { it.attr("id").endsWith("_cell") }
        assertTrue(cells.all { it.attr("layout_gravity") == "fill_horizontal|top" })
        assertEquals(listOf("recruit", "fatigue", "orders", "jade", "daily", "rest", "manufacture", "tower"),
            cells.map { it.attr("id").removePrefix("@+id/status_").removeSuffix("_cell") })
    }

    @Test fun nativeCandidateKeepsReferenceMetricsInlineAndUsesSupportedContainers() {
        val reference = xml("res/layout/status_widget_content.xml")
        val native = xml("res/layout/status_widget_native_v30_content.xml")
        assertTrue(native.children("GridLayout").isEmpty())
        assertEquals("10.8dp", native.attr("padding"))
        val referenceText = reference.children("TextView").associateBy { it.attr("id") }
        val nativeText = native.children("TextView").associateBy { it.attr("id")
            .replace("status_native_training_name", "status_training").replace("status_native_training_time", "status_training_time") }
        assertEquals(referenceText.keys, nativeText.keys)
        for ((id, text) in referenceText) assertEquals(text.getAttribute("style"), nativeText.getValue(id).getAttribute("style"))
        val referenceImages = reference.children("ImageView").associateBy { it.attr("id") }
        for (image in native.children("ImageView")) {
            val original = referenceImages.getValue(image.attr("id").replace("status_native_training_avatar", "status_trainee"))
            for (dimension in listOf("layout_width", "layout_height"))
                assertEquals(original.attr(dimension), image.attr(dimension))
        }
        assertTrue(native.children("ProgressBar").isEmpty())
        val bars = native.children("LinearLayout").filter { it.attr("id").endsWith("_progress") }
        assertEquals(setOf("@+id/status_jade_progress", "@+id/status_tower_progress"), bars.map { it.attr("id") }.toSet())
        for (bar in bars) {
            assertEquals("1.26dp", bar.attr("layout_height"))
            assertEquals("ltr", bar.attr("layoutDirection"))
            assertEquals("horizontal", bar.attr("orientation"))
            val fill = bar.children("LinearLayout").single()
            assertEquals("0dp", fill.attr("layout_width"))
            assertEquals("1", fill.attr("layout_weight"))
            assertEquals("gone", fill.attr("visibility"))
            assertEquals("no", fill.attr("importantForAccessibility"))
        }
        for (theme in listOf("dark", "light")) {
            val layers = xml("res/drawable/widget_progress_$theme.xml").children("item")
            assertEquals(listOf("@android:id/background", "@android:id/progress"), layers.map { it.attr("id") })
        }
        val containers = native.children("LinearLayout").associateBy { it.attr("id") }
        assertEquals("19.44dp", containers.getValue("@+id/status_header").attr("layout_height"))
        assertEquals("46.08dp", containers.getValue("@+id/status_summary").attr("layout_height"))
        for (cell in reference.children("LinearLayout").filter { it.attr("id").endsWith("_cell") }) {
            val candidate = containers.getValue(cell.attr("id"))
            assertEquals(cell.attr("orientation"), candidate.attr("orientation"))
            assertEquals(cell.attr("layout_height"), candidate.attr("layout_height"))
            val texts = candidate.children("TextView")
            assertEquals(2, texts.size)
            assertSame(texts[0].parentNode, texts[1].parentNode)
            assertEquals("0dp", texts[0].attr("layout_width"))
            assertEquals("1", texts[0].attr("layout_weight"))
            assertEquals("wrap_content", texts[1].attr("layout_width"))
        }
    }

    @Test fun hostUsesOnlyBasicViewsAndFitsTheFinishedReferenceCanvas() {
        for (theme in listOf("dark", "light")) {
        val host = xml("res/layout/status_widget_bitmap_$theme.xml")
        assertEquals("@android:id/background", host.attr("id"))
        val image = host.children("ImageView").single()
        assertEquals("fitCenter", image.attr("scaleType"))
        assertEquals("true", image.attr("adjustViewBounds"))
        assertTrue(host.children("GridLayout").isEmpty())
        assertTrue(host.children("include").isEmpty())
        assertTrue(host.children("Space").isEmpty())
        assertTrue(host.children("TextView").any { it.attr("id") == "@+id/status_refresh_hit" })
        assertEquals("@drawable/widget_host_$theme", host.attr("background"))
        val shape = xml("res/drawable/widget_host_$theme.xml")
        if (theme == "dark") {
            assertEquals("@color/widget_background_dark", shape.children("solid").single().attr("color"))
        }
        else assertEquals("@color/widget_background_$theme", shape.children("solid").single().attr("color"))
        }

    }

    @Test fun lightWidgetsUseTheDarkPaletteAtNightWithoutAProtectiveWhiteSurface() {
        val palette = xml("res/values-night/widget_palette.xml")
        val colors = palette.children("color").associate { it.getAttribute("name") to it.textContent }
        for (key in listOf("background", "text", "secondary", "label", "accent", "cell", "extra", "track", "progress"))
            assertEquals("@color/widget_${key}_dark", colors["widget_${key}_light"])
        assertEquals("@color/widget_secondary_dark", colors["widget_time_light"])
        val icons = palette.children("item").associate { it.getAttribute("name") to it.textContent }
        assertEquals("@drawable/widget_sanity_v2_dark", icons["widget_sanity_v2_light"])
        assertEquals("@drawable/widget_drone_v2_dark", icons["widget_drone_v2_light"])
        assertEquals("@drawable/widget_arknights_bg_night_dark", icons["widget_arknights_bg_light"])
        assertEquals("@drawable/avatar_placeholder_dark", icons["avatar_placeholder_light"])
        assertEquals("false", xml("res/layout/status_widget_native_v31_dark.xml").attr("forceDarkAllowed"))
        assertEquals("false", xml("res/layout/status_widget_native_v31_light.xml").attr("forceDarkAllowed"))
    }

    @Test fun bothThemeTextAndPlaceholderColorsHaveReadableContrast() {
        val colors = resolvedColors()
        val fill = luminance(colors.getValue("widget_progress_light"))
        val track = luminance(colors.getValue("widget_track_light"))
        assertTrue("Light progress needs visible contrast against its track", (track + .05) / (fill + .05) >= 3.0)
        for (theme in listOf("dark", "light")) {
            val background = colors.getValue("widget_background_$theme")
            val shape = xml("res/drawable/widget_background${if (theme == "light") "_light" else ""}.xml")
            assertEquals("shape", shape.nodeName)
            assertEquals((if (theme == "dark") "#E0" else "#E6") + background.takeLast(6), shape.children("solid").single().attr("color"))
            assertEquals("23.76dp", shape.children("corners").single().attr("radius"))
            val text = colors.getValue("widget_text_$theme")
            assertTrue("$theme text contrast is ${contrast(text, background)}", contrast(text, background) >= 7.0)
            // Summary cells are translucent: measure their text on the cell composited over the opaque theme surface.
            for (surface in listOf("cell", "extra")) {
                val cell = over(colors.getValue("widget_${surface}_$theme"), background)
                assertTrue("$theme $surface value contrast", contrast(text, cell) >= 7.0)
                val label = over(colors.getValue("widget_label_$theme"), cell)
                assertTrue("$theme $surface label contrast is ${contrast(label, cell)}", contrast(label, cell) >= 4.5)
            }
            val placeholder = xml("res/drawable/avatar_placeholder_$theme.xml").children("path").single()
            assertEquals("@color/widget_text_$theme", placeholder.attr("fillColor"))
        }
    }

    @Test fun summaryCellsAreTranslucentAndRefreshFollowsTheOfficialWidgetAfterDark() {
        val raw = xml("res/values/colors.xml").children("color").associate { it.getAttribute("name") to it.textContent.trim() }
        val colors = resolvedColors()
        for (surface in listOf("cell", "extra")) {
            // Light: 60% white lets the HyperOS host blur show through.
            assertEquals("#99FFFFFF", raw["widget_${surface}_light"])
            // Dark, and light widgets at night: official Skland #8C222222, translucent over artwork and blur.
            assertEquals("#8C222222", raw["widget_${surface}_dark"])
            for (theme in listOf("light", "dark")) {
                val alpha = argb(colors.getValue("widget_${surface}_$theme")) ushr 24
                assertTrue("$surface $theme must be translucent", alpha in 1L..254L)
            }
            val composite = over(colors.getValue("widget_${surface}_dark"), colors.getValue("widget_background_dark"))
            for (shift in listOf(16, 8, 0)) assertTrue("Dark $surface over #323232 is $composite",
                kotlin.math.abs(((argb(composite) shr shift) and 255) - 0x29) <= 1)
        }
        // Day light widgets keep the Miuix primary; dark widgets and light widgets at night use official sk_game_arknights.
        assertEquals("#FF3482FF", colors["widget_accent_light"])
        assertEquals("#FF22BBFF", raw["widget_accent_dark"])
    }

    @Test fun widgetPaletteUsesMiuixSemanticsAndPreservesGameArtwork() {
        val colors = xml("res/values/colors.xml").children("color").associate { it.getAttribute("name") to it.textContent }
        // Summary and extra cells left the Miuix container token for translucent surfaces (asserted separately).
        val expected = mapOf("background" to "surface", "text" to "on_surface", "secondary" to "summary",
            "time" to "summary", "label" to "summary", "accent" to "primary", "track" to "container", "progress" to "primary")
        for ((name, token) in expected) assertEquals("@color/miuix_widget_${token}_light", colors["widget_${name}_light"])
        assertEquals("#FF323232", colors["widget_background_dark"])
        for (name in listOf("sanity", "drone", "training")) {
            val icon = javax.imageio.ImageIO.read(File(System.getProperty("tsuki.projectDir"),
                "src/main/res/drawable-nodpi/widget_${name}_v2_light.png"))
            for (y in 0 until icon.height) for (x in 0 until icon.width) {
                val pixel = icon.getRGB(x, y)
                if (pixel.ushr(24) > 0) assertEquals(0x4B4B4B, pixel and 0xffffff)
            }
        }
    }

    @Test fun themeLayoutsHaveIndependentSurfacesAndShareAllContentIds() {
        for ((theme, drawable) in listOf("dark" to "widget_background", "light" to "widget_background_light")) {
            for (name in listOf("status_widget_$theme", "status_widget_render_$theme")) {
                val root = xml("res/layout/$name.xml")
                assertEquals("@android:id/background", root.attr("id"))
                assertEquals("match_parent", root.attr("layout_width"))
                assertEquals("match_parent", root.attr("layout_height"))
                assertEquals("@drawable/$drawable", root.attr("background"))
                assertEquals("@layout/status_widget_content", root.children("include").single().getAttribute("layout"))
                assertEquals("10.8dp", root.attr("paddingTop"))
                assertEquals(root.attr("paddingTop"), root.attr("paddingStart"))
            }
        }
        for (theme in listOf("dark", "light")) {
            val root = xml("res/layout/status_widget_native_v31_$theme.xml")
            val surface = root.children("ImageView").single()
            assertEquals(if (theme == "dark") "@+id/status_dark_surface" else "@+id/status_native_surface", surface.attr("id"))
            assertEquals("@drawable/widget_arknights_bg_$theme", surface.attr("src"))
            assertEquals("fitXY", surface.attr("scaleType"))
            assertEquals("true", root.attr("clipToOutline"))
            val layers = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
            assertEquals(listOf("ImageView", "include"), layers.map { it.nodeName })
            assertEquals("@drawable/widget_host_$theme", root.attr("background"))
            val native = root.children("include").single()
            assertEquals("match_parent", native.attr("layout_width"))
            assertEquals("match_parent", native.attr("layout_height"))
            val host = xml("res/layout/status_widget_scaled_$theme.xml")
            assertEquals("false", host.attr("clipChildren"))
            val canvas = host.children("include").single()
            assertEquals("360dp", canvas.attr("layout_width"))
            assertEquals("161.78988dp", canvas.attr("layout_height"))
            assertEquals("center", canvas.attr("layout_gravity"))
            assertEquals("@layout/status_widget_render_$theme", canvas.getAttribute("layout"))
        }
        val manifest = xml("AndroidManifest.xml")
        val version = manifest.children("application").single().children("meta-data")
            .single { it.attr("name") == "miuiWidgetVersion" }.attr("value").toInt()
        // 53: Endfield (2.2.0). 54: Endfield marks/texture. 55: Official Endfield & Arknights dark. 56: Endfield v3 & Arknights light.
        assertEquals(56, version)
        assertTrue(version > 1)
    }

    // Parses an explicit #AARRGGBB color; every palette value carries its alpha byte.
    private fun argb(hex: String): Long {
        assertTrue("$hex is not #AARRGGBB", Regex("#[0-9A-Fa-f]{8}").matches(hex))
        return hex.removePrefix("#").toLong(16)
    }

    // Source-over composite of `top` on an opaque `bottom`, returned as an opaque #AARRGGBB color.
    private fun over(top: String, bottom: String): String {
        val source = argb(top)
        val base = argb(bottom)
        assertEquals("Composite base $bottom must be opaque", 0xFFL, base ushr 24)
        val alpha = (source ushr 24) / 255.0
        val channels = listOf(16, 8, 0).map { shift ->
            Math.round(((source shr shift) and 255) * alpha + ((base shr shift) and 255) * (1 - alpha)).toInt()
        }
        return "#FF" + channels.joinToString("") { "%02X".format(it) }
    }

    // Relative luminance of an opaque color; translucent colors must be composited first.
    private fun luminance(hex: String): Double {
        val rgb = argb(hex)
        assertEquals("$hex must be composited before measuring luminance", 0xFFL, rgb ushr 24)
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(first: String, second: String): Double {
        val (high, low) = listOf(luminance(first), luminance(second)).sortedDescending()
        return (high + .05) / (low + .05)
    }
}
