package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.pages.AccountEditor
import cafe.miafetta.tsuki.ui.pages.PlatformSelector
import cafe.miafetta.tsuki.ui.state.AppRoute
import cafe.miafetta.tsuki.ui.state.rememberAppController
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme

import cafe.miafetta.tsuki.ui.pages.LicensesPage
import cafe.miafetta.tsuki.ui.pages.PrivacyPage

import android.app.Activity
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.semantics.*
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.StorageContract as Storage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.nav.core.*

// overviewWidgetId is the desktop widget whose tap started this launch; the overview switches to its role once.
@Composable
internal fun TsukiApp(activity: Activity, initialPage: Int, resumeTick: Int,
    configuringId: Int? = null, overviewWidgetId: Int? = null, onConfigured: () -> Unit = {},
    onConfigurationClosed: () -> Unit = {}) {
    val controller = rememberAppController(activity, initialPage, resumeTick, configuringId, overviewWidgetId, onConfigured)
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
                        onSave = { name, cred ->
                            scope.launch {
                                state.action(Storage.LOGIN, Bundle().apply {
                                    putString(Storage.NAME, name)
                                    putString(Storage.CRED, cred)
                                }, inlineError = true)
                            }
                        }, draft = draft, show = addingAccount, blurEnabled = blur,
                        pageProgress = if (transition.isRunning || transition.relativeDepth != 0f) 0f else 1f,
                        onPlatform = { if (AppRoute.SelectPlatform !in nav.backStack) nav.push(AppRoute.SelectPlatform) })
                }
                entry<AppRoute.SelectPlatform> { PlatformSelector(state.busy, blur) { if (!state.busy) nav.pop() } }
            }
        }
    }
}

// Secondary pages slide in inside one window, so the system window transition cannot round them; clip the entering
// page's leading corners to the screen corner radius as HyperOS does for activity transitions. Miuix defaults to 0dp.
@Composable
internal fun tsukiNavEffects() = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius(),
    blockInputDuringTransition = true)
