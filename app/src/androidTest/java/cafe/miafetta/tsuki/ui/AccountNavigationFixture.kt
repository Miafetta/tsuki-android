package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.glass.rememberNavActive
import cafe.miafetta.tsuki.ui.pages.AccountEditor
import cafe.miafetta.tsuki.ui.pages.PlatformSelector
import cafe.miafetta.tsuki.ui.pages.WebLoginPage
import cafe.miafetta.tsuki.ui.state.AppRoute
import cafe.miafetta.tsuki.ui.state.rememberAccountDraft

import android.app.Activity
import androidx.compose.runtime.*
import top.yukonga.miuix.kmp.nav.core.*

@Composable
internal fun AccountNavigationFixture(activity: Activity, show: Boolean, onDismiss: () -> Unit,
    onMessage: (String) -> Unit = {}, background: @Composable (Boolean) -> Unit) {
    val nav = rememberNavController<AppRoute>(AppRoute.Root)
    val draft = rememberAccountDraft()
    LaunchedEffect(show) {
        if (show && AppRoute.AddAccount !in nav.backStack) nav.push(AppRoute.AddAccount)
        if (!show) {
            draft.clear()
            nav.popUntil { it == AppRoute.Root }
        }
    }
    TsukiNavHost(navController = nav, onBack = {
        if (nav.backStack.last() == AppRoute.AddAccount) {
            draft.clear()
            onDismiss()
        } else nav.pop()
    }) {
        entry<AppRoute.Root> { TsukiNavEntry {
            val transition = LocalNavTransitionScope.current
            val running by remember(transition) { derivedStateOf { transition.isRunning } }
            background(show || running)
        } }
        entry<AppRoute.AddAccount> { TsukiNavEntry {
            AccountEditor(activity, false, "", onDismiss, { _, _ -> error("Fixture must not save credentials") },
                draft = draft, show = show, pageProgress = if (rememberNavActive()) 1f else 0f,
                onPlatform = { if (AppRoute.SelectPlatform !in nav.backStack) nav.push(AppRoute.SelectPlatform) },
                onWebLogin = { if (AppRoute.WebLogin !in nav.backStack) nav.push(AppRoute.WebLogin) }, onMessage = onMessage)
        } }
        entry<AppRoute.SelectPlatform> { TsukiNavEntry { PlatformSelector(false) { nav.pop() } } }
        // about:blank keeps device tests off the network; the official page is never loaded here.
        entry<AppRoute.WebLogin> { TsukiNavEntry {
            WebLoginPage(activity, onCred = { error("Fixture must not receive credentials") }, onBack = { nav.pop() },
                startUrl = "about:blank")
        } }
    }
}
