package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage

import androidx.core.content.edit
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.widget.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.*

internal data class Confirmation(val title: String, val summary: String, val method: String, val extras: Bundle)

@Stable
internal class AppController(
    val state: AppState,
    val preferences: SharedPreferences,
    val nav: NavController,
    val draft: AccountDraft,
    session: UiSession,
    private val activity: Activity,
) {
    var blur by mutableStateOf(preferences.getBoolean(UiPreferences.BLUR, true))
    var theme by mutableStateOf(preferences.getString(Storage.THEME, "SYSTEM") ?: "SYSTEM")
    var previewVersion by mutableIntStateOf(0)
    var confirmation by mutableStateOf<Confirmation?>(null)
    var lastConfirmation by mutableStateOf<Confirmation?>(null)
    var page by session::page
    var catalog by session::catalog
    var selectedKey by session::selectedKey
    var selectedGame by session::selectedGame
    var configSheet by session::configSheet
    var pendingPin by session::pendingPin
    var permissionDialog by session::permissionDialog
    var waitingSettings by session::waitingSettings
    val scope get() = state.viewModelScope
    val previewCache get() = state.previewCache
    val previewRender = WidgetPreviewSource(activity)::render
    val portraitLoad = TrainingPortraitSource(activity)::load
    val addingAccount get() = AppRoute.AddAccount in nav.backStack
    // Roles of the selected game tab; the overview shows one of them.
    val gameRoles get() = state.roles.filter { it.game == selectedGame }
    val selected get() = gameRoles.firstOrNull { it.key == selectedKey } ?: gameRoles.firstOrNull()
    val homeSnapshot get() = state.data.snapshot.takeIf { state.dataKey == selected?.key }

    // The catalog previews of every game: each game's own remembered role, read from the local cache.
    var catalogSnapshots by mutableStateOf(emptyMap<String, WidgetSnapshot?>())

    // The role a game tab shows: the selected one for the current tab, otherwise the last one shown in that game.
    fun gameRole(game: String): UiRole? {
        if (game == selectedGame) return selected
        val roles = state.roles.filter { it.game == game }
        val remembered = preferences.getString(UiPreferences.gameRole(game), null)
        return roles.firstOrNull { it.key == remembered } ?: roles.firstOrNull()
    }

    fun selectRole(role: UiRole) {
        selectedGame = role.game
        selectedKey = role.key
    }

    // A game tab returns to the role last shown in that game.
    fun selectGame(game: String) {
        if (game == selectedGame) return
        selectedGame = game
        selectedKey = preferences.getString(UiPreferences.gameRole(game), null)
            ?: preferences.getString(UiPreferences.ROLE, "")?.takeIf { gameOfKey(it) == game } ?: ""
    }

    fun changeTheme(value: String) {
        theme = value
        preferences.edit { putString(Storage.THEME, value) }
    }
    fun changeBlur(value: Boolean) {
        blur = value
        preferences.edit { putBoolean(UiPreferences.BLUR, value) }
    }
    fun refresh(role: UiRole? = selected) {
        if (role == null || role !in state.roles) return
        if (state.refreshing || state.busy) return
        state.refreshing = true
        state.error = ""
        scope.launch {
            try {
                state.dashboard(role, true)
                state.load()
            } finally { state.refreshing = false }
        }
    }
    // updating is the account opened from "更新 Cred"; its name is prefilled and its Cred is never read back.
    fun addAccount(updating: UiAccount? = null) {
        if (AppRoute.AddAccount in nav.backStack || state.busy) return
        state.error = ""
        draft.clear()
        if (updating != null) {
            draft.name = updating.name
            draft.accountId = updating.id
        }
        nav.push(AppRoute.AddAccount)
    }
    // A blank Cred while updating re-verifies the account with its stored Cred, so the name can change on its own.
    fun saveAccount(name: String, cred: String) {
        val accountId = draft.accountId
        val keep = cred.isEmpty() && accountId != null
        val extras = Bundle().apply {
            putString(Storage.NAME, name)
            if (keep) putString(Storage.ACCOUNT_ID, accountId) else putString(Storage.CRED, cred)
        }
        scope.launch { state.action(if (keep) Storage.REVERIFY else Storage.LOGIN, extras, inlineError = true) }
    }
    fun place(config: Bundle) {
        val accepted = try {
            if (config.getBoolean("probe")) {
                val manager = AppWidgetManager.getInstance(activity)
                manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(
                    ComponentName(activity, XiaomiProbeProvider::class.java), null, null)
            } else PinWidgetReceiver.request(activity, config)
        } catch (_: RuntimeException) { false }
        if (!accepted) state.messages.send("当前桌面不支持从应用内添加，请在桌面的小部件列表中添加")
        pendingPin = null
        permissionDialog = false
        waitingSettings = false
    }
    fun pin(config: Bundle) {
        if (DesktopShortcutPermission.check(activity) == DesktopShortcutPermission.State.GRANTED) place(config)
        else {
            pendingPin = config
            permissionDialog = true
        }
    }
    // The resume path skips the overview read because the selected-role effect, also keyed by resumeTick, reads it.
    suspend fun reload(readDashboard: Boolean = true) {
        state.load()
        if (page == AppPage.WIDGETS) previewVersion++
        val role = selected
        if (readDashboard && role != null && page == AppPage.OVERVIEW) state.dashboard(role)
    }
}
