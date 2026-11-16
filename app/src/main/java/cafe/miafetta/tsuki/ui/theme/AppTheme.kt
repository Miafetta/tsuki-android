package cafe.miafetta.tsuki.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.*

// Sizes measured against HyperOS 4 system app captures on the same device. The top bar titles follow the Settings app
// (2026-11-15): its large title is about 30 sp (title1, Miuix 32) and its collapsed title about 17 sp (title3). Section
// titles (subtitle) keep the Miuix default.
internal val TsukiTextStyles = defaultTextStyles().let { defaults ->
    defaultTextStyles(
        title1 = defaults.title1.copy(fontSize = 30.sp),
        title2 = defaults.title2.copy(fontSize = 22.sp),
        title3 = defaults.title3.copy(fontSize = 17.sp),
        title4 = defaults.title4.copy(fontSize = 17.sp),
        headline1 = defaults.headline1.copy(fontSize = 16.sp),
        headline2 = defaults.headline2.copy(fontSize = 15.sp),
        main = defaults.main.copy(fontSize = 16.sp),
        button = defaults.button.copy(fontSize = 16.sp),
        paragraph = defaults.paragraph.copy(fontSize = 16.sp),
        body1 = defaults.body1.copy(fontSize = 15.sp),
        body2 = defaults.body2.copy(fontSize = 13.sp),
        footnote1 = defaults.footnote1.copy(fontSize = 12.sp),
        footnote2 = defaults.footnote2.copy(fontSize = 10.sp),
    )
}

private val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
internal fun isTsukiDarkTheme(): Boolean = LocalDarkTheme.current

@Composable
internal fun TsukiAppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val controller = remember(mode) { ThemeController(colorSchemeMode = when (mode) {
        "LIGHT" -> ColorSchemeMode.Light
        "DARK" -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }) }
    val activity = LocalActivity.current
    SideEffect { activity?.let {
        val bars = WindowCompat.getInsetsController(it.window, it.window.decorView)
        bars.isAppearanceLightStatusBars = !dark
        bars.isAppearanceLightNavigationBars = !dark
    } }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MiuixTheme(controller = controller, textStyles = TsukiTextStyles, content = content)
    }
}
