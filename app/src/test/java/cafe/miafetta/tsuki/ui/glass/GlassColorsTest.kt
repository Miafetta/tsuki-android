package cafe.miafetta.tsuki.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

// TsukiAppTheme gives ThemeController no colour schemes, so the app shows Miuix's lightColorScheme() and darkColorScheme().
// Targets are the HyperOS 4 theme store and Phone app as recorded on 2026-11-14, in (299R + 587G + 114B) / 1000.
class GlassColorsTest {
    private val light = lightColorScheme()
    private val dark = darkColorScheme()

    private fun luminance(color: Color): Int {
        val argb = color.toArgb()
        return ((argb shr 16 and 255) * 299 + (argb shr 8 and 255) * 587 + (argb and 255) * 114) / 1000
    }

    @Test fun selectedChipMatchesTheThemeStoreAtRest() {
        val (lightChip, lightText) = chipSelectionColors(light, false)
        val (darkChip, darkText) = chipSelectionColors(dark, true)
        assertEquals(1f, lightChip.alpha, 0f)
        assertEquals(1f, darkChip.alpha, 0f)
        assertEquals(57f, luminance(lightChip).toFloat(), 2f)
        assertEquals(230f, luminance(darkChip).toFloat(), 2f)
        assertEquals(light.surface, lightText)
        assertEquals(dark.surface, darkText)
    }

    @Test fun unselectedChipAtRestIsDarkerOnlyInDark() {
        assertEquals(light.surfaceContainerHigh, chipIdleColor(light, false))
        val idle = chipIdleColor(dark, true)
        assertEquals(1f, idle.alpha, 0f)
        assertEquals(16f, luminance(idle.compositeOver(Color.Black)).toFloat(), 2f)
        assertTrue(luminance(idle) < luminance(dark.surfaceContainerHigh))
    }

    @Test fun darkDockBodyOnBlackMatchesTheSystemDock() {
        for (native in listOf(true, false)) {
            val tint = glassTint(dark, true, GlassSurface.Dock, native)
            assertEquals(.52f, tint.alpha, 1f / 255f)
            assertEquals(21f, luminance(tint.compositeOver(Color.Black)).toFloat(), 2f)
        }
    }

    @Test fun buttonAndLightDockTintsKeepTheirValues() {
        val darkBase = lerp(dark.surfaceContainer, dark.onSurface, .02f)
        for (native in listOf(true, false)) {
            assertEquals(light.surfaceContainer.copy(alpha = .62f), glassTint(light, false, GlassSurface.Button, native))
            assertEquals(darkBase.copy(alpha = .62f), glassTint(dark, true, GlassSurface.Button, native))
        }
        assertEquals(light.surfaceContainer.copy(alpha = .65f), glassTint(light, false, GlassSurface.Dock, true))
        assertEquals(light.surfaceContainer.copy(alpha = .41f), glassTint(light, false, GlassSurface.Dock, false))
    }

    @Test fun unselectedGlassChipTakesTheDockMaterialOnLight() {
        for (native in listOf(true, false)) {
            assertEquals(glassTint(light, false, GlassSurface.Dock, native), chipGlassTint(light, false, false, native))
        }
        // Colour alpha is stored in 8 bits.
        assertEquals(.65f, chipGlassTint(light, false, false, true).alpha, 1f / 255f)
        assertEquals(.41f, chipGlassTint(light, false, false, false).alpha, 1f / 255f)
        // Dark keeps its tint: 55% of the base lifted 4% towards onSurface, on both backends.
        val darkTint = lerp(dark.surfaceContainer, dark.onSurface, .04f).copy(alpha = .55f)
        for (native in listOf(true, false)) assertEquals(darkTint, chipGlassTint(dark, true, false, native))
    }

    @Test fun selectedGlassChipIsOpaqueOnLightAndNearlySoOnDark() {
        for (native in listOf(true, false)) {
            val lightChip = chipGlassTint(light, false, true, native)
            val darkChip = chipGlassTint(dark, true, true, native)
            assertEquals(chipSelectionColors(light, false).first, lightChip)
            assertEquals(1f, lightChip.alpha, 0f)
            assertEquals(chipSelectionColors(dark, true).first.copy(alpha = .94f), darkChip)
        }
    }

    @Test fun unselectedChipBlursLikeTheDock() {
        assertEquals(14f, surfaceBlurRadius(GlassSurface.Dock), 0f)
        assertEquals(surfaceBlurRadius(GlassSurface.Dock), surfaceBlurRadius(GlassSurface.Chip, selected = false), 0f)
        assertEquals(20f, surfaceBlurRadius(GlassSurface.Chip, selected = true), 0f)
        assertEquals(20f, surfaceBlurRadius(GlassSurface.Button), 0f)
        // The dock and the button do not depend on a selection.
        assertEquals(surfaceBlurRadius(GlassSurface.Dock), surfaceBlurRadius(GlassSurface.Dock, selected = true), 0f)
        assertEquals(surfaceBlurRadius(GlassSurface.Button), surfaceBlurRadius(GlassSurface.Button, selected = true), 0f)
    }
}
