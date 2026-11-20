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
import androidx.compose.ui.semantics.*
import cafe.miafetta.tsuki.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.nav.core.*

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
            NavDisplay(navController = nav, onBack = {
                if (!state.busy) {
                    state.error = ""
                    nav.pop()
                }
            }, effects = tsukiNavEffects()) {
                entry<AppRoute.Root> {
                    AppRoot(controller, activity, initialPage, configuringId, onConfigurationClosed)
                }
                entry<AppRoute.Privacy> { PrivacyPage(blur) { nav.pop() } }
                entry<AppRoute.Licenses> { LicensesPage(blur) { nav.pop() } }
                entry<AppRoute.Reminders> {
                    RemindersPage(state.reminders, state.accounts, blur, onAccount = { account ->
                        if (nav.backStack.lastOrNull() == AppRoute.Reminders) nav.push(AppRoute.ReminderAccount(account.id))
                    }) { nav.pop() }
                }
                entry<AppRoute.ReminderAccount> { route ->
                    ReminderAccountPage(state.reminders, state.accounts.firstOrNull { it.id == route.accountId }, state.loaded,
                        blur, onGone = { if (nav.backStack.lastOrNull() == route) nav.pop() }) { nav.pop() }
                }
                entry<AppRoute.AddAccount> {
                    val transition = LocalNavTransitionScope.current
                    AccountEditor(activity, state.busy, state.error,
                        onDismiss = {
                            if (!state.busy) {
                                draft.clear()
                                state.error = ""
                                nav.pop()
                            }
                        },
                        onSave = controller::saveAccount, draft = draft, show = addingAccount, blurEnabled = blur,
                        pageProgress = if (transition.isRunning || transition.relativeDepth != 0f) 0f else 1f,
                        onPlatform = { if (AppRoute.SelectPlatform !in nav.backStack) nav.push(AppRoute.SelectPlatform) },
                        onWebLogin = { if (AppRoute.WebLogin !in nav.backStack) nav.push(AppRoute.WebLogin) },
                        onMessage = { state.messages.send(it) }, lookupName = state::platformName)
                }
                entry<AppRoute.SelectPlatform> { PlatformSelector(state.busy, blur) { if (!state.busy) nav.pop() } }
                entry<AppRoute.WebLogin> {
                    // The Cred is filled into the editor for the user to review; nothing is submitted automatically.
                    WebLoginPage(activity, onCred = { cred ->
                        draft.cred = cred
                        state.messages.send("已填入网页登录取得的 Cred，确认后点击“验证并添加”。")
                        if (nav.backStack.lastOrNull() == AppRoute.WebLogin) nav.pop()
                    }, onBack = { if (nav.backStack.lastOrNull() == AppRoute.WebLogin) nav.pop() })
                }
            }
        }
    }
}

// Secondary pages slide in inside one window, so the system window transition cannot round them; clip the entering
// page's leading corners to the screen corner radius as HyperOS does for activity transitions. Miuix defaults to 0dp.
@Composable
internal fun tsukiNavEffects() = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius(),
    blockInputDuringTransition = true)
