package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.pages.AccountEditor
import cafe.miafetta.tsuki.ui.pages.PlatformSelector
import cafe.miafetta.tsuki.ui.pages.WebLoginPage
import cafe.miafetta.tsuki.ui.state.AppRoute
import cafe.miafetta.tsuki.ui.state.rememberAppController
import cafe.miafetta.tsuki.ui.theme.BrandLogos
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme

import cafe.miafetta.tsuki.ui.pages.LicensesPage
import cafe.miafetta.tsuki.ui.pages.PrivacyPage
import cafe.miafetta.tsuki.ui.pages.ReminderAccountPage
import cafe.miafetta.tsuki.ui.pages.RemindersPage

import android.app.Activity
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.ui.glass.rememberNavActive

// overviewWidgetId is the desktop widget whose tap started this launch, overviewRoleKey the role of the reminder
// notification that did; the overview switches to that role once.
@Composable
internal fun TsukiApp(activity: Activity, initialPage: Int, resumeTick: Int,
    configuringId: Int? = null, overviewWidgetId: Int? = null, overviewRoleKey: String? = null, onConfigured: () -> Unit = {},
    onConfigurationClosed: () -> Unit = {}) {
    BrandLogos.preloadAsync(activity.resources)
    val controller = rememberAppController(activity, initialPage, resumeTick, configuringId, overviewWidgetId, overviewRoleKey,
        onConfigured)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    with(controller) {
        LaunchedEffect(state, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.messages.events.collect { state.notifier.show(it.text) }
            }
        }
        TsukiAppTheme(theme) {
            TsukiNavHost(navController = nav, onBack = {
                if (!state.busy) {
                    state.error = ""
                    nav.pop()
                }
            }) {
                entry<AppRoute.Root> { TsukiNavEntry {
                    AppRoot(controller, activity, initialPage, configuringId, onConfigurationClosed)
                } }
                entry<AppRoute.Privacy> { TsukiNavEntry { PrivacyPage(blur) { nav.pop() } } }
                entry<AppRoute.Licenses> { TsukiNavEntry { LicensesPage(blur) { nav.pop() } } }
                entry<AppRoute.Reminders> { TsukiNavEntry {
                    RemindersPage(state.reminders, state.accounts, blur, onAccount = { account ->
                        if (nav.backStack.lastOrNull() == AppRoute.Reminders) nav.push(AppRoute.ReminderAccount(account.id))
                    }) { nav.pop() }
                } }
                entry<AppRoute.ReminderAccount> { route -> TsukiNavEntry {
                    ReminderAccountPage(state.reminders, state.accounts.firstOrNull { it.id == route.accountId }, state.loaded,
                        blur, onGone = { if (nav.backStack.lastOrNull() == route) nav.pop() }) { nav.pop() }
                } }
                entry<AppRoute.AddAccount> { TsukiNavEntry {
                    val pageActive = rememberNavActive()
                    AccountEditor(activity, state.busy, state.error,
                        onDismiss = {
                            if (!state.busy) {
                                draft.clear()
                                state.error = ""
                                nav.pop()
                            }
                        },
                        onSave = controller::saveAccount, draft = draft, show = addingAccount, blurEnabled = blur,
                        pageProgress = if (pageActive) 1f else 0f,
                        onPlatform = { if (AppRoute.SelectPlatform !in nav.backStack) nav.push(AppRoute.SelectPlatform) },
                        onWebLogin = { if (AppRoute.WebLogin !in nav.backStack) nav.push(AppRoute.WebLogin) },
                        onMessage = { state.messages.send(it) }, lookupName = state::platformName)
                } }
                entry<AppRoute.SelectPlatform> { TsukiNavEntry {
                    PlatformSelector(state.busy, blur) { if (!state.busy) nav.pop() }
                } }
                entry<AppRoute.WebLogin> { TsukiNavEntry {
                    // The Cred is filled into the editor for the user to review; nothing is submitted automatically.
                    WebLoginPage(activity, onCred = { cred ->
                        draft.cred = cred
                        state.messages.send("已填入网页登录取得的 Cred，确认后点击“验证并添加”。")
                        if (nav.backStack.lastOrNull() == AppRoute.WebLogin) nav.pop()
                    }, onBack = { if (nav.backStack.lastOrNull() == AppRoute.WebLogin) nav.pop() })
                } }
            }
        }
    }
}
