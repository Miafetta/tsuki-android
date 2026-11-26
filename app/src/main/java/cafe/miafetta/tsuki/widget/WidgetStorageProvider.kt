package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.StorageContract as Storage

import androidx.core.net.toUri
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import cafe.miafetta.tsuki.core.AccountProblemException
import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.SnapshotJson
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetTheme
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.OfficialAvatars
import org.json.JSONArray
import org.json.JSONObject

// Private IPC between the app process and :widgetProvider; only this app's UID may call it.
class WidgetStorageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == Process.myUid())
        val context = requireNotNull(context)
        return try {
            val runtime = WidgetRuntime.get(context)
            when (method) {
                Storage.OVERVIEW -> synchronized(runtime) { overview(context, runtime) }
                Storage.DASHBOARD, Storage.REFRESH_ROLE -> dashboard(runtime, method, extras)
                Storage.AVATAR -> Bundle().apply { putByteArray(Storage.BYTES, runtime.avatar(extras?.getString(Storage.URL))) }
                Storage.PREVIEW -> preview(runtime, extras)
                Storage.CHARACTERS -> characters(runtime)
                // ACCOUNT_ID is sent only by "更新 Cred" with a new Cred: the account it updates.
                Storage.LOGIN -> Bundle().also {
                    runtime.login(requireNotNull(extras?.getString(Storage.NAME)), requireNotNull(extras.getString(Storage.CRED)),
                        extras.getString(Storage.ACCOUNT_ID))
                }
                Storage.REVERIFY -> Bundle().also {
                    runtime.reverify(requireNotNull(extras?.getString(Storage.ACCOUNT_ID)), requireNotNull(extras.getString(Storage.NAME)))
                }
                Storage.PLATFORM_NAME -> Bundle().apply {
                    putString(Storage.NAME, runtime.platformName(requireNotNull(extras?.getString(Storage.CRED))))
                }
                Storage.LOGOUT -> Bundle().also { runtime.logout(requireNotNull(extras?.getString(Storage.ACCOUNT_ID))) }
                Storage.RESET_ACCOUNTS -> Bundle().also { runtime.resetAccounts() }
                Storage.PREPARE_PIN -> Bundle().also {
                    requireNotNull(extras)
                    runtime.preparePin(requireNotNull(extras.getString(Storage.PIN_TOKEN)), extras.widgetConfig(1))
                }
                Storage.CLEAR_PIN -> Bundle().also {
                    runtime.clearPin(extras?.getString(Storage.PIN_TOKEN),
                        extras?.takeIf { it.containsKey(Storage.PIN_CANCEL_BEFORE) }?.getLong(Storage.PIN_CANCEL_BEFORE))
                }
                Storage.CONFIGURE -> configure(context, runtime, extras)
                Storage.REMINDERS, Storage.SAVE_REMINDER, Storage.DELETE_REMINDER, Storage.SET_REMINDERS_ENABLED ->
                    reminders(runtime, method, extras)
                else -> throw IllegalArgumentException("Unsupported storage operation")
            }
        } catch (failure: DataSourceFailure) {
            errorBundle(failure.reason.name)
        } catch (_: java.io.IOException) {
            errorBundle()
        } catch (failure: AccountProblemException) {
            // Before IllegalArgumentException, which it extends: the editor names the rule that refused the account.
            errorBundle(failure.problem.name)
        } catch (_: IllegalArgumentException) {
            errorBundle()
        } catch (_: org.json.JSONException) {
            errorBundle()
        }
    }

    private fun overview(context: Context, runtime: WidgetRuntime): Bundle {
        val accounts = runtime.listAccounts()
        val accountIds = accounts.map { it.id }.toSet()
        val data = JSONObject()
        data.put(Storage.ACCOUNTS, JSONArray(accounts.map { account ->
            JSONObject().put(Storage.ID, account.id).put(Storage.NAME, account.name)
                .put(Storage.ROLES, JSONArray(account.roles.map { role ->
                    JSONObject().put(Storage.ID, role.uid).put(Storage.LABEL, role.label)
                        .put(Storage.PROVIDER_ID, role.game.providerId).put(Storage.SERVER, role.server)
                }))
        }))
        data.put(Storage.WIDGETS, JSONArray(StatusWidgetProvider.owned(context).map { id ->
            val record = runtime.read(id, accountIds)
            val config = record?.config
            JSONObject().put(Storage.ID, id).put(Storage.ACCOUNT_ID, config?.accountId)
                .put(Storage.CHARACTER_ID, config?.characterId).put(Storage.PROVIDER_ID, config?.providerId)
                .put(Storage.THEME, (config?.theme ?: StatusWidgetProvider.theme(context, id)).name)
                .put(Storage.STATE, record?.state?.name)
                .put(Storage.FAILURE, record?.failure?.name)
                .put(Storage.SIMULATE_FAILURE, config?.simulateFailure ?: false)
                .put(Storage.SNAPSHOT, record?.snapshot?.let(SnapshotJson::write))
        }))
        return Bundle().apply { putString(Storage.JSON, data.toString()) }
    }

    private fun dashboard(runtime: WidgetRuntime, method: String, extras: Bundle?): Bundle {
        val provider = requireNotNull(extras?.getString(Storage.PROVIDER_ID))
        val account = requireNotNull(extras.getString(Storage.ACCOUNT_ID))
        val character = requireNotNull(extras.getString(Storage.CHARACTER_ID))
        if (method == Storage.REFRESH_ROLE) runtime.refreshRole(provider, account, character)
        val data = runtime.dashboard(provider, account, character)
        if (method == Storage.REFRESH_ROLE) runtime.prepareAvatar(data.snapshot?.avatarUrl)
        val training = data.snapshot?.training
        val trainee = training?.avatarUrl ?: training?.traineeId?.let(OfficialAvatars::character)
        // A plain read waits only for a portrait never cached; renewing a cached one happens in the background.
        if (method == Storage.REFRESH_ROLE) runtime.prepareAvatar(trainee) else runtime.prepareAvatarForRead(trainee)
        return Bundle().apply {
            data.snapshot?.let { putString(Storage.SNAPSHOT, SnapshotJson.write(it).toString()) }
            putString(Storage.FAILURE, data.failure?.name)
            putLong(Storage.RETRY_AT, data.retryAt)
        }
    }

    private fun preview(runtime: WidgetRuntime, extras: Bundle?): Bundle {
        val source = extras?.getString(Storage.SNAPSHOT)
        require(source == null || source.length <= 128 * 1024)
        val snapshot = source?.let { SnapshotJson.read(JSONObject(it)) }
        val theme = WidgetTheme.valueOf(requireNotNull(extras?.getString(Storage.THEME)))
        val feature = extras.getString(Storage.FEATURE_ID) ?: WidgetRuntime.FEATURE
        return Bundle().apply { putParcelable(Storage.BITMAP, runtime.preview(snapshot, theme, feature)) }
    }

    private fun characters(runtime: WidgetRuntime): Bundle = Bundle().apply {
        val characters = runtime.characters()
        putStringArrayList(Storage.ACCOUNT_IDS, ArrayList(characters.map { (_, character) -> character.accountId }))
        putStringArrayList(Storage.CHARACTER_IDS, ArrayList(characters.map { (_, character) -> character.characterId }))
        putStringArrayList(Storage.LABELS, ArrayList(characters.map { (_, character) -> character.label }))
        putStringArrayList(Storage.PROVIDER_IDS, ArrayList(characters.map { (providerId, _) -> providerId }))
    }

    // Configuration writes are accepted only for widget instances this app actually owns.
    private fun configure(context: Context, runtime: WidgetRuntime, extras: Bundle?): Bundle {
        val id = requireNotNull(extras).getInt(Storage.WIDGET_ID, -1)
        require(id in StatusWidgetProvider.owned(context))
        // A widget shows one game: its provider decides which feature it may be configured with.
        require(extras.getString(Storage.FEATURE_ID) == StatusWidgetProvider.feature(context, id))
        val token = extras.getString(Storage.PIN_TOKEN)
        if (token == null) runtime.configure(extras.widgetConfig(id))
        else runtime.configurePin(extras.widgetConfig(id), token)
        return Bundle()
    }

    // Rules are checked where they are saved (WidgetRuntimeReminders.kt); an invalid rule is an invalid request.
    private fun reminders(runtime: WidgetRuntime, method: String, extras: Bundle?): Bundle {
        val data = when (method) {
            Storage.SAVE_REMINDER -> runtime.saveReminder(requireNotNull(extras).reminderRule())
            Storage.DELETE_REMINDER -> runtime.deleteReminder(requireNotNull(extras).reminderSubject(),
                requireNotNull(extras.getString(Storage.METRIC_ID)))
            Storage.SET_REMINDERS_ENABLED -> runtime.setRemindersEnabled(requireNotNull(extras).flag(Storage.ENABLED))
            else -> runtime.reminderData()
        }
        return Bundle().apply { putString(Storage.JSON, ReminderAccess.overview(data).toString()) }
    }

    private fun errorBundle(failure: String? = null) = Bundle().apply {
        putBoolean(Storage.ERROR, true)
        if (failure != null) putString(Storage.FAILURE, failure)
    }

    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?) = throw UnsupportedOperationException()

    companion object {
        val URI: Uri = "content://cafe.miafetta.tsuki.widgets".toUri()
    }
}

private fun Bundle.widgetConfig(id: Int) = WidgetInstanceConfig(id,
    requireNotNull(getString(Storage.PROVIDER_ID)), requireNotNull(getString(Storage.FEATURE_ID)),
    requireNotNull(getString(Storage.ACCOUNT_ID)), requireNotNull(getString(Storage.CHARACTER_ID)),
    WidgetTheme.valueOf(requireNotNull(getString(Storage.THEME))), getBoolean(Storage.SIMULATE_FAILURE))

private fun Bundle.reminderSubject() = ReminderSubject(requireNotNull(getString(Storage.PROVIDER_ID)),
    requireNotNull(getString(Storage.ACCOUNT_ID)), requireNotNull(getString(Storage.CHARACTER_ID)))

// A threshold is sent only for the modes that take one; the switch of the rule must always be sent.
private fun Bundle.reminderRule() = ReminderRule(reminderSubject(), requireNotNull(getString(Storage.METRIC_ID)),
    ReminderMode.valueOf(requireNotNull(getString(Storage.MODE))),
    if (containsKey(Storage.THRESHOLD)) getInt(Storage.THRESHOLD) else null, flag(Storage.ENABLED))

private fun Bundle.flag(key: String): Boolean {
    require(containsKey(key))
    return getBoolean(key)
}
