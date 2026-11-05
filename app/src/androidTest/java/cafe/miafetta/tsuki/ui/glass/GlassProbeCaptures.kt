package cafe.miafetta.tsuki.ui.glass

import cafe.miafetta.tsuki.ui.state.AppRoute

import cafe.miafetta.tsuki.ui.pages.AccountEditor
import cafe.miafetta.tsuki.ui.pages.PlatformSelector
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.components.TsukiNavDock
import cafe.miafetta.tsuki.ui.pages.LicensesPage
import cafe.miafetta.tsuki.ui.pages.PrivacyPage
import cafe.miafetta.tsuki.ui.shell

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.core.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Collapsed captures of the secondary pages in both palettes.
internal fun GlassProbe.secondaryPages() {
    val route = mutableStateOf<AppRoute>(AppRoute.AddAccount)
    runner.runOnMainSync {
        (host as ComponentActivity).setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, 1f), LocalForceMiuixGlass provides forceMiuix.value) {
                TsukiAppTheme(mode.value) {
                    key(route.value, mode.value) {
                        val nav = rememberNavController<AppRoute>(route.value)
                        NavDisplay(navController = nav, onBack = {}) {
                            entry<AppRoute.AddAccount> {
                                AccountEditor(host, false, "", {}, { _, _ -> error("Fixture must not save credentials") })
                            }
                            entry<AppRoute.SelectPlatform> { PlatformSelector(false) {} }
                            entry<AppRoute.Privacy> { PrivacyPage(true) {} }
                            entry<AppRoute.Licenses> { LicensesPage(true) {} }
                        }
                    }
                }
            }
        }
    }
    for (palette in listOf("LIGHT", "DARK")) {
        for ((page, name) in listOf(AppRoute.AddAccount to "account", AppRoute.SelectPlatform to "platform",
            AppRoute.Privacy to "privacy", AppRoute.Licenses to "licenses")) {
            runner.runOnMainSync {
                mode.value = palette
                route.value = page
            }
            settle()
            val x = host.window.decorView.width / 2
            val y = host.window.decorView.height * 3 / 4
            runner.uiAutomation.shell("input swipe $x $y $x ${y - 150} 700")
            settle()
            capture("$name-${palette.lowercase()}-collapsed").recycle()
        }
    }
}

// Recreate the Activity with in-memory UI only; no account/settings/widget writes.
internal fun GlassProbe.recreation() {
    val hostRunner = runner as cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
    val recreated = CountDownLatch(1)
    var replacement: ComponentActivity? = null
    hostRunner.uiContentOnCreate = { activity ->
        replacement = activity
        activity.setContent {
            TsukiAppTheme("LIGHT") {
                TsukiGlassHost(true) {
                    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                        Canvas(Modifier.fillMaxSize().tsukiBackdrop()) {
                            repeat(80) { i -> drawRect(if (i % 2 == 0) Color.Cyan else Color.Magenta,
                                Offset(i * 16.dp.toPx(), 0f), Size(16.dp.toPx(), size.height)) }
                        }
                        TsukiGlass({ 1f }, 92.dp, Modifier.fillMaxWidth().height(138.dp))
                        TsukiNavDock(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
                    }
                }
            }
        }
        activity.window.decorView.postDelayed({ recreated.countDown() }, 800)
    }
    try {
        runner.runOnMainSync { host.recreate() }
        check(recreated.await(10, TimeUnit.SECONDS)) { "Activity recreation timed out" }
        check(replacement !== host && replacement != null)
        capture("probe-recreated").recycle()
        File(folder, "glass-recreation-result.txt").writeText("PASS: new Activity created and background rendered; in-memory fixture only")
    } finally {
        hostRunner.uiContentOnCreate = null
        runner.runOnMainSync { replacement?.finish() }
    }
}
