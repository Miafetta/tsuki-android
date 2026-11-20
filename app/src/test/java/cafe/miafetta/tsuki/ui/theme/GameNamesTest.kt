package cafe.miafetta.tsuki.ui.theme

import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.providers.SklandGame
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

// Game names are written once, in SklandGame: the pages (Branding) and reminder notifications show the same names.
class GameNamesTest {
    @Test fun pagesShowTheGameNamesOfSklandGame() {
        assertEquals(SklandGame.ARKNIGHTS.displayName, Branding.GAME)
        assertEquals(SklandGame.ENDFIELD.displayName, Branding.ENDFIELD)
        assertEquals("明日方舟", Branding.gameName(Branding.GAME_ID))
        assertEquals("终末地", Branding.gameName(Branding.ENDFIELD_ID))
        assertEquals(listOf(Branding.GAME_ID to "明日方舟", Branding.ENDFIELD_ID to "终末地"), Branding.games)
    }

    // The pages name a widget with its provider's label in strings.xml, so renaming a label renames the widget in the
    // launcher's list and in the app alike. Both Arknights providers carry the official name, without a palette.
    @Test fun widgetNamesAreTheProviderLabels() {
        assertEquals(R.string.status_widget_name, widgetNameRes(Branding.GAME_ID))
        assertEquals(R.string.endfield_widget_name, widgetNameRes(Branding.ENDFIELD_ID))
        val strings = xml("res/values/strings.xml").getElementsByTagName("string").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
        }
        assertEquals("明日方舟数据工具", strings["status_widget_name"])
        assertEquals("明日方舟数据工具", strings["status_widget_dark_name"])
        assertEquals("明日方舟：终末地数据工具", strings["endfield_widget_name"])
        for (name in listOf("status_widget_name", "status_widget_dark_name", "endfield_widget_name")) {
            assertFalse(name, listOf("浅色", "深色", "·").any { it in strings.getValue(name) })
        }
    }

    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File(System.getProperty("tsuki.projectDir"), "src/main/$path")).documentElement
}
