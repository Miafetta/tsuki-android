package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.storageCall
import cafe.miafetta.tsuki.failureMessage
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class AppState(context: Context, val notifier: UiNotifier = ToastUiNotifier(context),
    private val call: (String, Bundle) -> Bundle = { method, extras -> context.applicationContext.storageCall(method,
        extras) }) : AndroidViewModel(context.applicationContext as Application) {
    private val context get() = getApplication<Application>()
    var accounts by mutableStateOf(emptyList<UiAccount>())
    var widgets by mutableStateOf(emptyList<UiWidget>())
    var choices by mutableStateOf(emptyList<UiRole>())
    var busy by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var completedAction by mutableStateOf<String?>(null)
    val previewCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(8)
    var loaded by mutableStateOf(false)
    // Overview reads that ran to completion, successful or not; a cancelled read lists nothing and is not counted.
    var finishedLoads by mutableIntStateOf(0)
    val messages = UiMessages()
    private val readFailures = ReadFailureMessages(messages)
    var error by mutableStateOf("")
    var data by mutableStateOf(RoleData())
    var dataKey by mutableStateOf("")
    // The last data read for each role in this process, so switching game tabs or roles shows it at once instead of an
    // empty card until the read returns. Cleared when the accounts change.
    private val shownData = mutableMapOf<String, RoleData>()
    private var request = 0
    private var refreshingRole = ""
    private var initialLoad: Deferred<Unit>? = null
    val roles get() = accounts.flatMap { it.roles }

    // Starts the first overview read as soon as the screen asks for its state, so the IPC and a cold :widgetProvider
    // start overlap the first composition. Later calls are no-ops.
    fun startInitialLoad() {
        if (initialLoad == null && !loaded) initialLoad = viewModelScope.async { read() }
    }

    // While the initial read is still running, a load joins it instead of issuing a second read.
    suspend fun load() {
        initialLoad?.takeIf { it.isActive }?.let {
            it.await()
            return
        }
        read()
    }

    private suspend fun read() {
        try {
            val result = withContext(Dispatchers.IO) { call(Storage.OVERVIEW, Bundle()) }
            if (result.getBoolean(Storage.ERROR)) {
                messages.send(context.getString(failureMessage(result.getString(Storage.FAILURE))))
                return
            }
            // Parsing, including widget snapshots, stays off the main thread; only the state assignment runs there.
            val (loadedAccounts, loadedWidgets) = withContext(Dispatchers.Default) {
                val json = JSONObject(result.getString(Storage.JSON, "{}"))
                parseAccounts(json) to parseWidgets(json)
            }
            if (accounts.map { it.id } != loadedAccounts.map { it.id }) {
                previewCache.evictAll()
                shownData.clear()
            }
            accounts = loadedAccounts
            widgets = loadedWidgets
            if (!cafe.miafetta.tsuki.BuildConfig.DEBUG) {
                choices = roles
                return
            }
            val characters = withContext(Dispatchers.IO) { call(Storage.CHARACTERS, Bundle()) }
            val ids = characters.getStringArrayList(Storage.ACCOUNT_IDS) ?: arrayListOf()
            val roles = characters.getStringArrayList(Storage.CHARACTER_IDS) ?: arrayListOf()
            val labels = characters.getStringArrayList(Storage.LABELS) ?: arrayListOf()
            val providers = characters.getStringArrayList(Storage.PROVIDER_IDS) ?: arrayListOf()
            require(listOf(roles, labels, providers).all { it.size == ids.size })
            choices = ids.indices.map { i -> UiRole(ids[i], labels[i].substringBefore(" · "), roles[i], labels[i], providers[i]) }
        } catch (_: org.json.JSONException) { messages.send("数据读取失败，请重试") }
        catch (_: IllegalArgumentException) { messages.send("数据读取失败，请重试") }
        finally {
            loaded = true
            if (currentCoroutineContext().isActive) finishedLoads++
        }
    }

    suspend fun dashboard(role: UiRole, refresh: Boolean = false) {
        if (refreshingRole == role.key) return
        val token = ++request
        if (dataKey != role.key) {
            readFailures.clear()
            data = shownData[role.key] ?: RoleData()
            dataKey = role.key
        }
        if (refresh) refreshingRole = role.key
        val result = try {
            withContext(Dispatchers.IO) { call(if (refresh) Storage.REFRESH_ROLE else Storage.DASHBOARD, role.extras()) }
        } finally { if (refresh && refreshingRole == role.key) refreshingRole = "" }
        if (token != request || dataKey != role.key) return
        fun reportFailure(text: String) {
            if (refresh) messages.send(text) else readFailures.send(role, text)
        }
        if (result.getBoolean(Storage.ERROR)) {
            reportFailure(context.getString(failureMessage(result.getString(Storage.FAILURE))))
            return
        }
        try {
            val parsed = withContext(Dispatchers.Default) {
                RoleData(result.getString(Storage.SNAPSHOT)?.let { SnapshotJson.read(JSONObject(it)) },
                    result.getString(Storage.FAILURE)?.let(RefreshFailure::valueOf), result.getLong(Storage.RETRY_AT))
            }
            // Parsing suspended; a newer request or role switch wins.
            if (token != request || dataKey != role.key) return
            data = parsed
            shownData[role.key] = parsed
            readFailures.clear()
            if (refresh) data.failure?.takeIf { it != RefreshFailure.RATE_LIMIT }?.let {
                messages.send(context.getString(failureMessage(it.name)))
            }
        } catch (_: org.json.JSONException) { reportFailure("缓存数据已损坏，请刷新") }
        catch (_: IllegalArgumentException) { reportFailure("缓存数据已损坏，请刷新") }
    }

    suspend fun previewSnapshot(role: UiRole): WidgetSnapshot? = withContext(Dispatchers.IO) {
        val result = call(Storage.DASHBOARD, role.extras())
        if (result.getBoolean(Storage.ERROR)) return@withContext null
        try { result.getString(Storage.SNAPSHOT)?.let { SnapshotJson.read(JSONObject(it)) } }
        catch (_: org.json.JSONException) { null }
        catch (_: IllegalArgumentException) { null }
    }

    suspend fun action(method: String, extras: Bundle, inlineError: Boolean = false): Boolean {
        if (busy) return false
        busy = true
        error = ""
        return try {
            val result = withContext(Dispatchers.IO) { call(method, extras) }
            if (result.getBoolean(Storage.ERROR)) {
                val message = context.getString(failureMessage(result.getString(Storage.FAILURE)))
                if (inlineError) error = message else messages.send(message)
                false
            } else {
                load()
                completedAction = method
                true
            }
        } finally {
            extras.clear()
            busy = false
        }
    }
}

private fun parseAccounts(json: JSONObject): List<UiAccount> {
    val values = json.getJSONArray(Storage.ACCOUNTS)
    return (0 until values.length()).map { index ->
        val account = values.getJSONObject(index)
        val id = account.getString(Storage.ID)
        val name = account.getString(Storage.NAME)
        val roles = account.getJSONArray(Storage.ROLES)
        UiAccount(id, name, (0 until roles.length()).map { ri ->
            val role = roles.getJSONObject(ri)
            UiRole(id, name, role.getString(Storage.ID), role.getString(Storage.LABEL),
                role.optString(Storage.PROVIDER_ID, SklandArknightsAdapter.ID),
                role.optString(Storage.SERVER).takeIf { it.isNotEmpty() })
        }.filter { it.provider in SHOWN_PROVIDERS })
    }
}

// Games with an overview and a desktop widget. Roles of other games stay stored on the account but are not listed
// until their game is added here.
private val SHOWN_PROVIDERS = setOf(SklandArknightsAdapter.ID, SklandGame.ENDFIELD.providerId)

private fun parseWidgets(json: JSONObject): List<UiWidget> {
    val instances = json.getJSONArray(Storage.WIDGETS)
    return (0 until instances.length()).map { index ->
        val widget = instances.getJSONObject(index)
        UiWidget(widget.getInt(Storage.ID), widget.optString(Storage.ACCOUNT_ID), widget.optString(Storage.CHARACTER_ID),
            widget.optString(Storage.PROVIDER_ID), widget.optString(Storage.THEME, "DARK"),
            widget.optJSONObject(Storage.SNAPSHOT)?.let(SnapshotJson::read), widget.optString(Storage.FAILURE),
            widget.optBoolean(Storage.SIMULATE_FAILURE))
    }
}
