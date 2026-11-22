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
import cafe.miafetta.tsuki.ui.glass.rememberNavActive
import cafe.miafetta.tsuki.ui.glass.rememberNavGlassActive
import cafe.miafetta.tsuki.ui.theme.Branding

@Composable
internal fun AppRoot(controller: AppController, activity: Activity, initialPage: Int,
    configuringId: Int?, onConfigurationClosed: () -> Unit) = with(controller) {
    val active = rememberNavActive()
    val glassActive = rememberNavGlassActive()
    PredictiveBackHandler(enabled = page != AppPage.OVERVIEW && nav.backStack.last() == AppRoute.Root &&
        configSheet is ConfigSheet.Closed &&
        confirmation == null && !permissionDialog, onProgress = { events -> events.collect {} },
        onCommit = { if (initialPage != AppPage.OVERVIEW) activity.finish() else page = AppPage.OVERVIEW }, onCancel = {})
    AppScaffold(page, modifier = Modifier.semantics { if (!active) hideFromAccessibility() },
        canRefresh = selected != null && !state.refreshing && !state.busy, showRefresh = selected != null,
        blurEnabled = blur, active = active, glassActive = glassActive, onRefresh = { controller.refresh() },
        refreshing = state.refreshing,
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
                configSheet = ConfigSheet.New(game = selectedGame)
            }
        },
        addButtonVisible = !addingAccount && !state.busy,
        games = Branding.games, selectedGameId = selectedGame, onGameSelect = { selectGame(it) },
        overlays = {
            AppConfigSheet(controller, activity, configuringId, active, onConfigurationClosed)
            AppDialogs(controller, activity, active)
        }) { visiblePage ->
        appPages(visiblePage, controller, activity, blur)
    }
}
