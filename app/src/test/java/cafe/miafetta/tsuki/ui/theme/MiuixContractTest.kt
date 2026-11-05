package cafe.miafetta.tsuki.ui.theme

import cafe.miafetta.tsuki.ui.state.AccountDraft
import cafe.miafetta.tsuki.ui.state.AppRoute

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import top.yukonga.miuix.kmp.theme.*

class MiuixContractTest {
    @Test fun widgetAvatarPlaceholderKeepsTheActualMiuixIconGeometry() {
        val root = File(checkNotNull(System.getProperty("tsuki.projectDir")))
        val vector = MiuixIcons.ContactsCircle
        val group = vector.root[0] as VectorGroup
        val path = group[0] as VectorPath
        val ns = "http://schemas.android.com/apk/res/android"
        for (mode in listOf("light", "dark")) {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val xml = factory.newDocumentBuilder().parse(File(root, "src/main/res/drawable/avatar_placeholder_$mode.xml"))
            assertEquals(vector.viewportWidth, xml.documentElement.getAttributeNS(ns, "viewportWidth").toFloat(), .001f)
            val g = xml.getElementsByTagName("group").item(0) as org.w3c.dom.Element
            assertEquals(group.scaleY, g.getAttributeNS(ns, "scaleY").toFloat(), .001f)
            assertEquals(group.translationY, g.getAttributeNS(ns, "translateY").toFloat(), .001f)
            val p = xml.getElementsByTagName("path").item(0) as org.w3c.dom.Element
            assertEquals(path.pathData, PathParser().parsePathString(p.getAttributeNS(ns, "pathData")).toNodes())
        }
    }

    @Test fun widgetResourceProjectionMatchesTheInstalledMiuixDefaults() {
        val root = File(checkNotNull(System.getProperty("tsuki.projectDir")))
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "src/main/res/values/miuix_widget_tokens.xml"))
        fun values(tag: String) = xml.getElementsByTagName(tag).let { nodes ->
            (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent.trim()
            }
        }
        val types = defaultTextStyles()
        val dimensions = values("dimen")
        for ((name, style) in mapOf("title2" to types.title2, "title3" to types.title3,
            "body1" to types.body1, "body2" to types.body2, "footnote1" to types.footnote1)) {
            assertEquals(style.fontSize.value * .6f, dimensions.getValue("miuix_widget_$name").removeSuffix("dp").toFloat(), .001f)
        }
        assertEquals("12dp", dimensions["widget_header_text"])
        val colors = values("color")
        // The retained dark primary token still mirrors Miuix, although widget_accent_dark no longer references it.
        assertEquals(darkColorScheme().primary.toArgb().toLong() and 0xffffffffL,
            colors.getValue("miuix_widget_primary_dark").removePrefix("#").toLong(16))
        for ((mode, scheme) in mapOf("light" to lightColorScheme())) {
            for ((name, color) in mapOf("on_surface" to scheme.onSurface, "summary" to scheme.onSurfaceVariantSummary,
                "surface" to scheme.surface, "container" to scheme.surfaceContainer,
                "primary" to scheme.primary, "divider" to scheme.dividerLine)) {
                if (mode == "light" && name == "divider") continue
                assertEquals(color.toArgb().toLong() and 0xffffffffL,
                    colors.getValue("miuix_widget_${name}_$mode").removePrefix("#").toLong(16))
            }
        }
    }

    @Test fun navigationRestoresAllRoutesAndDraftNeverSavesCredentials() {
        val routes: List<AppRoute> = listOf(AppRoute.Root, AppRoute.Privacy, AppRoute.Licenses, AppRoute.AddAccount,
            AppRoute.SelectPlatform)
        assertEquals(routes, Json.decodeFromString<List<AppRoute>>(Json.encodeToString(routes)))
        val draft = AccountDraft("Test account").apply { cred = "synthetic-secret" }
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(AccountDraft.Saver) { scope.save(draft) }
        assertEquals("Test account", saved)
        val restored = AccountDraft.Saver.restore(checkNotNull(saved))!!
        assertEquals(draft.name, restored.name)
        assertEquals("", restored.cred)
    }
}
