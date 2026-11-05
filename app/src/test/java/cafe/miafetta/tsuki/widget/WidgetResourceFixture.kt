package cafe.miafetta.tsuki.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

// Shared fixtures and helpers for the classes split out of the former ProbeManifestTest.
abstract class WidgetResourceFixture {
    protected val androidNs = "http://schemas.android.com/apk/res/android"
    protected fun xml(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory.newDocumentBuilder().parse(
            File(System.getProperty("tsuki.projectDir"), "src/main/$path")).documentElement
    }
    protected fun Element.attr(name: String) = getAttributeNS(androidNs, name)
    protected fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    protected fun resolvedColors(): Map<String, String> {
        val entries = listOf("res/values/colors.xml", "res/values/miuix_widget_tokens.xml")
            .flatMap { xml(it).children("color") }.associate { it.getAttribute("name") to it.textContent.trim() }
        fun resolve(name: String): String {
            val value = entries.getValue(name)
            return if (value.startsWith("@color/")) resolve(value.removePrefix("@color/")) else value
        }
        return entries.keys.associateWith(::resolve)
    }

}
