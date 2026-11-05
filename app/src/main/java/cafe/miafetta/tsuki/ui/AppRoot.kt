package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.sheets.AppDialogs
import cafe.miafetta.tsuki.ui.state.AppPage
import cafe.miafetta.tsuki.ui.state.AppRoute
import cafe.miafetta.tsuki.ui.state.ConfigSheet


import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.StorageContract as Storage
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.nav.core.*
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler

import cafe.miafetta.tsuki.ui.state.AppController

import cafe.miafetta.tsuki.ui.sheets.AppConfigSheet

@Composable
internal fun AppRoot(controller: AppController, activity: Activity, initialPage: Int,
    configuringId: Int?, onConfigurationClosed: () -> Unit) = with(controller) {
    val transition = LocalNavTransitionScope.current
    val active = transition.relativeDepth == 0f && !transition.isRunning
    PredictiveBackHandler(enabled = page != AppPage.OVERVIEW && nav.backStack.last() == AppRoute.Root &&
        configSheet is ConfigSheet.Closed &&
        confirmation == null && !permissionDialog, onProgress = { events -> events.collect {} },
        onCommit = { if (initialPage != AppPage.OVERVIEW) activity.finish() else page = AppPage.OVERVIEW }, onCancel = {})
    AppScaffold(page, modifier = Modifier.semantics { if (!active) hideFromAccessibility() },
        canRefresh = selected != null && !state.refreshing && !state.busy, showRefresh = selected != null,
        blurEnabled = blur, glassActive = active, onRefresh = { controller.refresh() }, refreshing = state.refreshing,
        scrollToAppearance = initialPage == AppPage.APPEARANCE,
        appearanceIndex = if (state.loaded) 1 + state.accounts.size.coerceAtLeast(1) else null,
        onNavigate = {
            page = it
            state.error = ""
        },
        onAdd = {
            if (page == AppPage.ME) addAccount()
            else {
                state.error = ""
                configSheet = ConfigSheet.New()
            }
        },
        addButtonVisible = !addingAccount && !state.busy,
        overlays = {
            AppConfigSheet(controller, activity, configuringId, active, onConfigurationClosed)
            AppDialogs(controller, activity, active)
        }) { visiblePage ->
        appPages(visiblePage, controller, activity, blur)
    }
}
