package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage

import androidx.core.content.edit
import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.widget.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.*

@Composable
internal fun ControllerNavigationEffects(controller: AppController, activity: Activity,
    configuringId: Int?, onConfigured: () -> Unit) = with(controller) {
    var launchHandled by rememberSaveable { mutableStateOf(false) }
    // Runs on every creation: a restored web login page keeps its session, anything else left behind is wiped.
    LaunchedEffect(Unit) { if (AppRoute.WebLogin !in nav.backStack) WebLoginData.wipeIfStale(activity) }
    LaunchedEffect(Unit) {
        if (!launchHandled) {
            launchHandled = true
            if (activity.intent.getBooleanExtra("addAccount", false)) addAccount()
        }
    }
    LaunchedEffect(addingAccount) {
        if (!addingAccount) {
            draft.clear()
            state.error = ""
        }
    }
    LaunchedEffect(configuringId) {
        if (configuringId != null) {
            page = AppPage.WIDGETS
            configSheet = ConfigSheet.Edit(configuringId)
        }
    }
    LaunchedEffect(state.completedAction) {
        when (state.completedAction) {
            Storage.LOGIN, Storage.REVERIFY -> {
                draft.clear()
                nav.popUntil { it == AppRoute.Root }
            }
            Storage.CONFIGURE -> {
                configSheet = configSheet.close()
                onConfigured()
            }
            Storage.LOGOUT, Storage.RESET_ACCOUNTS -> confirmation = null
        }
        state.completedAction = null
    }
}

@Composable
internal fun ControllerDataEffects(controller: AppController, activity: Activity, resumeTick: Int,
    overviewWidgetId: Int?) = with(controller) {
    val awaitingOverview = overviewTargetPending(controller, overviewWidgetId)
    var loadedResume by remember { mutableStateOf<Int?>(null) }
    // Once per activity: the first overview read has finished and the frame showing it is drawn. This is what
    // `am start -W` and the system report as "Fully drawn".
    var fullyDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(state.finishedLoads) {
        if (!fullyDrawn && state.finishedLoads > 0) {
            fullyDrawn = true
            withFrameNanos {}
            activity.reportFullyDrawn()
        }
    }
    LaunchedEffect(page, catalog, resumeTick) {
        val resumed = loadedResume != resumeTick
        if (resumed) theme = preferences.getString(Storage.THEME, "SYSTEM") ?: "SYSTEM"
        if (resumed || page == AppPage.WIDGETS) reload(readDashboard = false)
        loadedResume = resumeTick
        if (waitingSettings) {
            waitingSettings = false
            pendingPin?.let {
                if (DesktopShortcutPermission.check(activity) == DesktopShortcutPermission.State.GRANTED) place(it)
                else permissionDialog = true
            }
        }
    }
    DisposableEffect(activity) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scope.launch { reload() } }
        }
        activity.contentResolver.registerContentObserver(WidgetStorageProvider.URI, false, observer)
        onDispose { activity.contentResolver.unregisterContentObserver(observer) }
    }
    val selected = selected
    // While a widget tap is unresolved, skip the remembered role so it is not written back or refreshed in its place.
    LaunchedEffect(selected?.key, page, resumeTick, awaitingOverview) {
        if (selected != null && page == AppPage.OVERVIEW && !awaitingOverview) {
            selectedKey = selected.key
            preferences.edit { putString(UiPreferences.ROLE, selected.key) }
            state.dashboard(selected)
            if (dashboardNeedsRefresh(state.data.snapshot, System.currentTimeMillis()) &&
                state.data.failure == null && state.error.isEmpty() && !state.refreshing) {
                state.refreshing = true
                scope.launch { try { state.dashboard(selected, true) } finally { state.refreshing = false } }
            }
        }
    }
}

// A widget tap selects that widget's role once, after a load has finished listing widgets and roles; a load cancelled
// by rotation or a page switch does not count, otherwise the tap would be spent on empty lists. The pending id is
// saved, so neither recomposition nor recreation overrides a later manual switch; a new tap recreates MainActivity with
// fresh saved state and applies again. An unknown, unbound or removed binding, or a failed read, keeps the selection.
@Composable
private fun overviewTargetPending(controller: AppController, overviewWidgetId: Int?): Boolean = with(controller) {
    var target by rememberSaveable(overviewWidgetId) { mutableStateOf(overviewWidgetId) }
    LaunchedEffect(target, state.finishedLoads) {
        val id = target
        if (id == null || state.finishedLoads == 0) return@LaunchedEffect
        widgetRoleKey(state.widgets, state.roles, id)?.let {
            selectedKey = it
            page = AppPage.OVERVIEW
        }
        target = null
    }
    target != null
}
