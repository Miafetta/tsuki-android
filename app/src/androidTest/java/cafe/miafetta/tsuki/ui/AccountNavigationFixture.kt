package cafe.miafetta.tsuki.ui

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
    NavDisplay(navController = nav, onBack = {
        if (nav.backStack.last() == AppRoute.AddAccount) {
            draft.clear()
            onDismiss()
        } else nav.pop()
    }, effects = tsukiNavEffects()) {
        entry<AppRoute.Root> {
            val active = show || LocalNavTransitionScope.current.isRunning
            background(active)
        }
        entry<AppRoute.AddAccount> {
            AccountEditor(activity, false, "", onDismiss, { _, _ -> error("Fixture must not save credentials") },
                draft = draft, show = show, pageProgress = if (LocalNavTransitionScope.current.isRunning ||
                    LocalNavTransitionScope.current.relativeDepth != 0f) 0f else 1f,
                onPlatform = { if (AppRoute.SelectPlatform !in nav.backStack) nav.push(AppRoute.SelectPlatform) },
                onWebLogin = { if (AppRoute.WebLogin !in nav.backStack) nav.push(AppRoute.WebLogin) }, onMessage = onMessage)
        }
        entry<AppRoute.SelectPlatform> { PlatformSelector(false) { nav.pop() } }
        // about:blank keeps device tests off the network; the official page is never loaded here.
        entry<AppRoute.WebLogin> {
            WebLoginPage(activity, onCred = { error("Fixture must not receive credentials") }, onBack = { nav.pop() },
                startUrl = "about:blank")
        }
    }
}
