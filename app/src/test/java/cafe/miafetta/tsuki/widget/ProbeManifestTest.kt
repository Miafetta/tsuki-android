package cafe.miafetta.tsuki.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

// Manifest and resource contracts: stable resource IDs, widget receivers, private storage, backup rules and network security.
class ProbeManifestTest : WidgetResourceFixture() {
    // Startup shows the placeholder card and the picker a sample card; neither may be a blank surface (HyperOS widget rules).
    @Test fun startupShowsTheLoadingCardAndThePickerAFilledPreviewForEachPalette() {
        val colors = resolvedColors()
        // Arknights has a light and a dark card; the Endfield card is always dark, like the official widget.
        val cards = listOf(Triple("status_widget_info", "status_widget_%s_light", "widget_host_light"),
            Triple("status_widget_dark_info", "status_widget_%s_dark", "widget_host_dark"),
            Triple("endfield_widget_info", "endfield_widget_%s", "widget_endfield_surface"))
        for ((info, layout, surface) in cards) {
            val file = xml("res/xml/$info.xml")
            assertEquals("@layout/${layout.format("loading")}", file.attr("initialLayout"))
            assertEquals("@layout/${layout.format("preview")}", file.attr("previewLayout"))
            assertEquals("4", file.attr("targetCellWidth"))
            assertEquals("2", file.attr("targetCellHeight"))
            for (kind in listOf("loading", "preview")) {
                val root = xml("res/layout/${layout.format(kind)}.xml")
                assertEquals("@android:id/background", root.attr("id"))
                assertEquals("match_parent", root.attr("layout_width"))
                assertEquals("match_parent", root.attr("layout_height"))
                assertEquals("false", root.attr("forceDarkAllowed"))
                assertEquals("@drawable/$surface", root.attr("background"))
                val fill = xml("res/drawable/$surface.xml").children("solid").single().attr("color")
                val argb = colors.getValue(fill.removePrefix("@color/")).removePrefix("#")
                assertTrue("$kind $surface card surface is transparent", argb.length == 6 || argb.take(2) != "00")
                val elements = listOf(root) + root.children("*")
                assertTrue("$kind $surface hides content", elements.none { it.attr("visibility") in listOf("invisible", "gone") })
                assertTrue(root.children("include").isEmpty())
            }
        }
    }

    @Test fun compiledWidgetResourceIdsKeepTheirReleasedMeaningAndNewLayoutIdentity() {
        val pins = File(System.getProperty("tsuki.projectDir"), "widget-resource-ids.txt").readLines()
        val retired = setOf("status_widget_native_content", "status_widget_native_dark", "status_widget_native_light",
            "status_widget_native_v22_dark", "status_widget_native_v22_light",
            "status_widget_native_v24_dark", "status_widget_native_v24_light", "status_widget_native_v25_dark",
            "status_widget_native_v26_dark", "status_widget_native_v22_content",
            "status_widget_native_v25_light", "status_widget_native_v27_dark", "status_widget_native_v28_content",
            "status_widget_native_v28_dark", "status_widget_native_v28_light") +
            // HyperOS keeps a placed widget's bitmap for an unchanged drawable ID, so a redrawn bitmap takes a new name.
            setOf("drawable/widget_endfield_band", "drawable/widget_endfield_band_text")
        val numbers = mutableSetOf<Int>()
        for (line in pins) {
            val entry = Regex("cafe.miafetta.tsuki:(\\w+)/([\\w.]+) = (0x[0-9a-f]+)").matchEntire(line)!!
            val (type, name, hex) = entry.destructured
            val expected = hex.removePrefix("0x").toLong(16).toInt()
            assertTrue("Resource ID assigned twice: $hex", numbers.add(expected))
            // Deleted layouts and bitmaps keep their numbers reserved for cached hosts.
            if (type == "layout" && name in retired || "$type/$name" in retired) continue
            val resourceClass = Class.forName("cafe.miafetta.tsuki.R\$$type")
            assertEquals("$type/$name changed its released ID", expected, resourceClass.getField(name.replace('.', '_')).getInt(null))
        }
        assertEquals(0x7f050077, cafe.miafetta.tsuki.R.id.status_recruit_cell)
        assertEquals(0x7f070024, cafe.miafetta.tsuki.R.layout.status_widget_native_v30_dark)
        assertEquals(0x7f070025, cafe.miafetta.tsuki.R.layout.status_widget_native_v30_light)
        val gradle = File(System.getProperty("tsuki.projectDir"), "build.gradle.kts").readText()
        assertTrue(gradle.contains("--stable-ids"))
    }

    @Test fun everyRemainingWidgetHasHyperOsCapabilitiesAndAndroidProbeIsRemoved() {
        val manifest = xml("AndroidManifest.xml")
        val receivers = manifest.children("receiver").filter { it.children("meta-data").any { meta ->
            meta.attr("name") == "android.appwidget.provider" } }
        assertEquals("false", manifest.children("receiver").single {
            it.attr("name").endsWith("PinWidgetReceiver") }.attr("exported"))
        assertEquals(4, receivers.size)
        assertFalse(receivers.any { it.attr("name").endsWith("AndroidProbeProvider") })
        for (receiver in receivers) {
            assertEquals(":widgetProvider", receiver.attr("process"))
            assertTrue(receiver.children("meta-data").any { it.attr("name") == "android.appwidget.provider" })
            val metadata = receiver.children("meta-data").associate { it.attr("name") to it.attr("value") }
            assertEquals("true", metadata["miuiWidget"])
            assertEquals("exposure", metadata["miuiWidgetRefresh"])
            assertEquals("20000", metadata["miuiWidgetRefreshMinInterval"])
            assertTrue(receiver.children("action").any { it.attr("name") == "miui.appwidget.action.APPWIDGET_UPDATE" })
        }
        assertFalse(File(System.getProperty("tsuki.projectDir"), "src/main/res/layout/activity_main.xml").exists())
        assertEquals(listOf("com.android.launcher.permission.INSTALL_SHORTCUT", "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"),
            manifest.children("uses-permission").map { it.attr("name") })
    }

    @Test fun probeUsesFixedFourByTwoAndRequiredBackgroundId() {
        val info = xml("res/xml/probe_widget_info.xml")
        assertEquals("300dp", info.attr("minWidth"))
        assertEquals("110dp", info.attr("minHeight"))
        assertEquals("4", info.attr("targetCellWidth"))
        val modern = xml("res/xml/probe_widget_info.xml")
        assertEquals("4", modern.attr("targetCellWidth"))
        assertEquals("2", modern.attr("targetCellHeight"))
        assertEquals("@layout/probe_widget", modern.attr("previewLayout"))
        assertEquals("none", info.attr("resizeMode"))
        assertEquals("0", info.attr("updatePeriodMillis"))
        assertEquals("@android:id/background", xml("res/layout/probe_widget.xml").attr("id"))
    }

    @Test fun configurationStorageIsPrivateAndOnlyWidgetProcessWritesIt() {
        val manifest = xml("AndroidManifest.xml")
        val provider = manifest.children("provider").single()
        assertEquals("false", provider.attr("exported"))
        assertEquals(":widgetProvider", provider.attr("process"))
        val receiver = manifest.children("receiver").single { it.attr("name").endsWith("StatusWidgetProvider") }
        assertEquals(":widgetProvider", receiver.attr("process"))
        val info = xml("res/xml/status_widget_info.xml")
        assertEquals("cafe.miafetta.tsuki.WidgetConfigActivity", info.attr("configure"))
        assertEquals("@android:id/background", xml("res/layout/status_widget.xml").attr("id"))
    }

    @Test fun backupRulesExcludeEveryUsedStorageDomain() {
        val rules = xml("res/xml/data_extraction_rules.xml")
        for (mode in listOf("cloud-backup", "device-transfer")) {
            val excludes = rules.children(mode).single().children("exclude")
            assertEquals(setOf("root", "file", "database", "sharedpref", "external"),
                excludes.map { it.getAttribute("domain") }.toSet())
            assertTrue(excludes.all { it.getAttribute("path") == "." })
        }
    }

    @Test fun networkWorkerAndAccountScreenArePrivateAndCleartextIsDisabled() {
        val manifest = xml("AndroidManifest.xml")
        val application = manifest.children("application").single()
        assertEquals("false", application.attr("usesCleartextTraffic"))
        val service = manifest.children("service").single()
        assertEquals("false", service.attr("exported"))
        assertEquals("android.permission.BIND_JOB_SERVICE", service.attr("permission"))
        assertEquals(":widgetProvider", service.attr("process"))
        assertEquals("false", manifest.children("activity").single {
            it.attr("name").endsWith("SklandAccountActivity") }.attr("exported"))
        val screen = File(System.getProperty("tsuki.projectDir"), "src/main/java/cafe/miafetta/tsuki/ui/pages/AccountEditor.kt").readText()
        assertTrue(screen.contains("PasswordVisualTransformation()"))
        assertFalse(screen.contains("var cred by rememberSaveable"))
        assertFalse(screen.contains("WindowManager.LayoutParams.FLAG_SECURE"))
        // No predictive back anywhere (docs/UI_DESIGN_CONVENTIONS.md): back runs on release, without gesture previews.
        assertEquals("false", manifest.children("application").single().attr("enableOnBackInvokedCallback"))
    }
    @Test fun arknightsDeclaresHostBackgroundBlurOnItsReceiver() {
        val receiver = xml("AndroidManifest.xml").children("receiver").single {
            it.attr("name").endsWith("StatusWidgetProvider")
        }
        val support = receiver.children("meta-data").single { it.attr("name") == "supportBackgroundBlur" }
        assertEquals("true", support.attr("value"))
        val receivers = xml("AndroidManifest.xml").children("receiver")
        val opaque = receivers.single { it.attr("name") == ".widget.OpaqueWidgetProvider" }
        // The Endfield card draws its own dark surface, like the dark Arknights card.
        val endfield = receivers.single { it.attr("name") == ".widget.EndfieldWidgetProvider" }
        val probe = receivers.single { it.attr("name").endsWith("XiaomiProbeProvider") }
        for (card in listOf(opaque, endfield, probe))
            assertFalse(card.children("meta-data").any { it.attr("name") == "supportBackgroundBlur" })
        val info = xml("res/xml/status_widget_dark_info.xml")
        assertEquals("cafe.miafetta.tsuki.WidgetConfigActivity", info.attr("configure"))
        assertEquals("4", info.attr("targetCellWidth"))
        assertEquals("2", info.attr("targetCellHeight"))
    }

}
