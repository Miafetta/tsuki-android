package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.core.WidgetTheme
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

// Loading placeholder, picker preview and tap target: which card a record gets and what its static layouts contain.
class WidgetPlaceholderTest : WidgetResourceFixture() {
    private val config = WidgetInstanceConfig(7, "fake.skland", WidgetRuntime.FEATURE, "demo-1", "amiya")
    private val snapshot = FakeArknightsAdapter().fetch(config, 1700000000000L)
    private val palettes = listOf("light", "dark")
    private val res = File(System.getProperty("tsuki.projectDir"), "src/main/res")

    @Test fun onlyAFirstFetchWithoutDataShowsTheLoadingCard() {
        val first = WidgetRecord(config, null, RefreshState.LOADING)
        assertTrue(showsLoadingPlaceholder(first, false))
        assertFalse(showsLoadingPlaceholder(first, true))
        assertFalse(showsLoadingPlaceholder(null, false))
        assertFalse(showsLoadingPlaceholder(null, true))
        assertFalse(showsLoadingPlaceholder(first.copy(snapshot = snapshot), false))
        assertFalse(showsLoadingPlaceholder(first.copy(state = RefreshState.ERROR, failure = RefreshFailure.NETWORK), false))
        assertFalse(showsLoadingPlaceholder(first.copy(state = RefreshState.READY), false))
        assertEquals(R.layout.status_widget_loading_light, loadingLayout(WidgetTheme.LIGHT))
        assertEquals(R.layout.status_widget_loading_dark, loadingLayout(WidgetTheme.DARK))
        assertNotEquals(loadingLayout(WidgetTheme.LIGHT), loadingLayout(WidgetTheme.DARK))
    }

    @Test fun onlyAnUnconfiguredCardOpensConfigurationAndItsUriDiffersFromOverview() {
        assertEquals(WidgetTapTarget.CONFIGURE, widgetTapTarget(null, false))
        assertEquals(WidgetTapTarget.OVERVIEW, widgetTapTarget(null, true))
        for (state in RefreshState.entries) for (data in listOf(null, snapshot)) {
            assertEquals(WidgetTapTarget.OVERVIEW, widgetTapTarget(WidgetRecord(config, data, state), false))
        }
        // The launcher edit entry and WidgetConfigActivity.widgetConfigId both expect exactly this form.
        assertEquals("tsuki://configure/7?miuiWidgetId=7", widgetConfigureUri(7))
    }

    @Test fun loadingCardsKeepStaticPartsAndReplaceEveryTextWithPalettePlaceholders() {
        for (palette in palettes) {
            val root = xml("res/layout/status_widget_loading_$palette.xml")
            assertEquals("@string/status_loading", root.attr("contentDescription"))
            assertTrue("Loading $palette shows text", root.children("TextView").isEmpty())
            val sources = root.children("ImageView").map { it.attr("src") }.filter { it.isNotEmpty() }
            assertEquals(listOf("@drawable/widget_placeholder_circle_$palette", "@drawable/widget_refresh",
                "@drawable/widget_sanity_$palette", "@drawable/widget_clock_$palette", "@drawable/widget_drone_$palette",
                "@drawable/widget_clock_$palette", "@drawable/widget_clock_$palette"), sources)
            val shapes = root.children("ImageView").map { it.attr("background") }.filter { it.isNotEmpty() }
            assertTrue(shapes.all { it in placeholderShapes(palette) })
            // One per text bar, the training portrait and the two progress lines.
            assertEquals(loadingBars.size + 3, shapes.size)
            val cells = (root.children("FrameLayout") + root.children("LinearLayout")).map { it.attr("background") }
            assertEquals(4, cells.count { it == "@drawable/widget_cell_$palette" })
            assertEquals(2, cells.count { it == "@drawable/widget_extra_$palette" })
            assertEquals(1, root.children("LinearLayout").count { it.attr("id") == "@+id/status_refresh_hit" })
            assertPaletteOnly(root, palette)
        }
        val night = xml("res/values-night/widget_placeholder.xml").children("color").associate {
            it.getAttribute("name") to it.textContent.trim()
        }
        assertEquals(mapOf("widget_placeholder_light" to "@color/widget_placeholder_dark"), night)
    }

    // Palette resources cannot be swapped inside a shared include, so the two loading layouts mirror each other.
    @Test fun loadingLayoutsDifferOnlyInTheirPalette() {
        fun structure(palette: String) = xml("res/layout/status_widget_loading_$palette.xml").let { listOf(it) + it.children("*") }
            .map { element ->
                val attributes = (0 until element.attributes.length).map { element.attributes.item(it) }
                element.tagName to attributes.associate { it.nodeName to it.nodeValue.replace("_$palette", "_palette") }.toSortedMap()
            }
        assertEquals(structure("light"), structure("dark"))
    }

    // The cold initial layout sizes itself from weights and minimums alone; the runtime card then sets the same reference
    // sizes exactly (WidgetLoadingSizing.kt), so both must keep the numbers in step.
    @Test fun loadingLayoutsUseTheRuntimeReferenceSizesAndPinnedIds() {
        val pins = File(System.getProperty("tsuki.projectDir"), "widget-resource-ids.txt").readLines()
        fun name(id: Int) = R.id::class.java.fields.single { it.getInt(null) == id }.name
        fun Element.dp(name: String) = attr(name).removeSuffix("dp").toFloatOrNull() ?: 0f
        for (palette in palettes) {
            val root = xml("res/layout/status_widget_loading_$palette.xml")
            val elements = root.children("*").filter { it.attr("id").isNotEmpty() }.associateBy { it.attr("id").substringAfter('/') }
            assertEquals(CardSizer.INSET, root.dp("padding"), 0f)
            assertEquals(CardSizer.HEADER, elements.getValue("status_header").attr("layout_weight").toFloat(), 0f)
            assertEquals(CardSizer.PRIMARY, elements.getValue("status_primary").attr("layout_weight").toFloat(), .01f)
            assertEquals(CardSizer.SUMMARY, elements.getValue("status_summary").attr("layout_weight").toFloat(), 0f)
            assertEquals(CardSizer.PRIMARY_GAP, elements.getValue("status_primary").dp("layout_marginTop"), 0f)
            for (bar in loadingBars) {
                val element = elements.getValue(name(bar.id))
                assertEquals("Loading $palette ${name(bar.id)}", listOf(bar.width, bar.height, bar.lineMargin, bar.lineMargin),
                    listOf(element.dp("minWidth"), element.dp("minHeight"), element.dp("layout_marginTop"),
                        element.dp("layout_marginBottom")))
            }
            for (id in elements.keys) {
                assertTrue("Loading $palette ID $id is not pinned", pins.any { it.startsWith("cafe.miafetta.tsuki:id/$id ") })
            }
            assertFalse(elements.containsKey("status_title") || elements.containsKey("status_native_content"))
        }
    }

    @Test fun pickerPreviewsShowSampleValuesWithTheSharedSummaryLabels() {
        val labels = listOf("recruit", "fatigue", "orders", "jade", "daily", "rest", "manufacture", "tower").map { "status_label_$it" }
        assertEquals(labels.map { R.string::class.java.getField(it).getInt(null) }, summaryLabelTexts)
        val strings = xml("res/values/strings.xml").children("string").associate { it.getAttribute("name") to it.textContent }
        for (palette in palettes) {
            val root = xml("res/layout/status_widget_preview_$palette.xml")
            val texts = root.children("TextView").map { it.attr("text") }
            assertEquals(labels.map { "@string/$it" }, texts.filter { it.startsWith("@string/status_label_") })
            // Header 3, primary metrics 6, summary cells 8 labels and 8 values.
            assertEquals(25, texts.size)
            for (text in texts) {
                val value = strings.getValue(text.removePrefix("@string/"))
                assertTrue("Preview $palette shows $value", value.isNotBlank() && "未知" !in value)
            }
            assertTrue(root.children("TextView").all { it.attr("textColor").endsWith("_$palette") })
            assertEquals(listOf("3", "7"), root.children("LinearLayout").map { it.attr("weightSum") }.filter { it.isNotEmpty() })
            assertPaletteOnly(root, palette)
        }
    }

    // A light card takes the whole dark palette at night (values-night). Every color and drawable its static layouts use,
    // including those a drawable uses inside, must have a night variant or use only resources that have one; a tinted
    // white icon follows its tint. The dark layouts must not reach any light resource at all.
    @Test fun staticLightLayoutsFollowNightModeAndDarkLayoutsNeverUseLightResources() {
        val night = nightVariants()
        for (kind in listOf("preview", "loading")) {
            for ((reference, tinted) in references(xml("res/layout/status_widget_${kind}_light.xml"))) {
                assertTrue("$kind light uses $reference, which stays light at night", followsNight(reference, tinted, night))
            }
            for (reference in references(xml("res/layout/status_widget_${kind}_dark.xml")).flatMap { closure(it.first) }) {
                assertFalse("$kind dark uses $reference", reference.endsWith("_light"))
            }
        }
    }

    private fun placeholderShapes(palette: String) = setOf("@drawable/widget_placeholder_$palette",
        "@drawable/widget_placeholder_circle_$palette", "@color/widget_placeholder_$palette")

    // Light layouts follow night mode through their *_light resources; the dark card stays dark. Mixing them would not.
    private fun assertPaletteOnly(root: Element, palette: String) {
        val other = if (palette == "light") "dark" else "light"
        for (element in listOf(root) + root.children("*")) for (index in 0 until element.attributes.length) {
            val value = element.attributes.item(index).nodeValue
            if (value.startsWith("@color/") || value.startsWith("@drawable/")) {
                assertFalse("$palette layout uses $value", value.endsWith("_$other"))
                assertTrue("$palette layout uses $value", value == "@drawable/widget_refresh" || value.endsWith("_$palette"))
            }
        }
    }

    // Each color or drawable reference in a file, and whether it is an image source the element tints.
    private fun references(root: Element): List<Pair<String, Boolean>> = (listOf(root) + root.children("*")).flatMap { element ->
        (0 until element.attributes.length).map { element.attributes.item(it) }.filter { isReference(it.nodeValue) }
            .map { it.nodeValue to (it.localName == "src" && element.attr("tint").isNotEmpty()) }
    }

    private fun isReference(value: String) = value.startsWith("@color/") || value.startsWith("@drawable/")

    // Names with a night variant: values-night entries and drawable-night files.
    private fun nightVariants(): Set<String> {
        val values = File(res, "values-night").listFiles().orEmpty().filter { it.extension == "xml" }.flatMap { file ->
            val root = xml("res/values-night/${file.name}")
            // Typed items are resource aliases; untyped ones belong to styles.
            (root.children("color") + root.children("item").filter { it.getAttribute("type").isNotEmpty() }).map {
                "@${it.getAttribute("type").ifEmpty { it.tagName }}/${it.getAttribute("name")}"
            }
        }
        return (values + File(res, "drawable-night").listFiles().orEmpty().map { "@drawable/${it.nameWithoutExtension}" }).toSet()
    }

    private val colorValues by lazy {
        File(res, "values").listFiles().orEmpty().filter { it.extension == "xml" }
            .flatMap { xml("res/values/${it.name}").children("color") }.associate { it.getAttribute("name") to it.textContent.trim() }
    }

    // A drawable's own color values: attribute references and literals; bitmaps have none to inspect.
    private fun drawableValues(name: String): List<String>? {
        if (!File(res, "drawable/$name.xml").exists()) return null
        val root = xml("res/drawable/$name.xml")
        return (listOf(root) + root.children("*")).flatMap { element ->
            (0 until element.attributes.length).map { element.attributes.item(it).nodeValue }
        }.filter { isReference(it) || it.startsWith("#") }
    }

    private fun followsNight(reference: String, tinted: Boolean, night: Set<String>): Boolean {
        if (reference in night) return true
        val name = reference.substringAfter('/')
        val values = if (reference.startsWith("@color/")) listOfNotNull(colorValues[name]) else drawableValues(name) ?: return false
        return values.isNotEmpty() && values.all { value ->
            if (isReference(value)) followsNight(value, false, night) else tinted || isTransparent(value)
        }
    }

    private fun isTransparent(value: String) = value.length == 9 && value.substring(1, 3) == "00"

    // A reference with everything it resolves to.
    private fun closure(reference: String): List<String> {
        val name = reference.substringAfter('/')
        val values = if (reference.startsWith("@color/")) listOfNotNull(colorValues[name]) else drawableValues(name).orEmpty()
        return listOf(reference) + values.filter(::isReference).flatMap(::closure)
    }
}
