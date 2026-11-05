package cafe.miafetta.tsuki.widget

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.Settings
import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.FileWidgetRepository
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.RoleData
import cafe.miafetta.tsuki.core.RoleDataStore
import cafe.miafetta.tsuki.core.WidgetDefinition
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetPinRequest
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTheme
import cafe.miafetta.tsuki.core.WidgetUpdateCoordinator
import cafe.miafetta.tsuki.providers.AccountRepository
import cafe.miafetta.tsuki.providers.AndroidCredentialKey
import cafe.miafetta.tsuki.providers.AvatarCache
import cafe.miafetta.tsuki.providers.CredentialCipher
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.FileAccountRepository
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandClient
import cafe.miafetta.tsuki.providers.SklandHttpsTransport
import java.io.File

// Process-wide state of the :widgetProvider process. Account sign-in and sign-out live in WidgetRuntimeAccounts.kt.
internal class WidgetRuntime private constructor(internal val application: Application) {
    internal val repository = FileWidgetRepository(File(application.noBackupFilesDir, "widgets"))
    private val pinRequest = WidgetPinRequest(File(application.noBackupFilesDir, "pending-pin.properties"))
    internal val accounts: AccountRepository = FileAccountRepository(File(application.noBackupFilesDir, "accounts/v1.bin"),
        CredentialCipher(AndroidCredentialKey::get))
    internal val client = SklandClient(SklandHttpsTransport())
    private val fake = FakeArknightsAdapter()
    internal val roleData = RoleDataStore(File(application.noBackupFilesDir, "roles"))
    internal val skland = SklandArknightsAdapter(accounts, client, onSnapshot = { account, role, snapshot ->
        roleData.write(account, role, RoleData(snapshot))
    })
    private val adapters = listOf(fake, skland)
    internal val coordinator = WidgetUpdateCoordinator(repository,
        mapOf(FEATURE to WidgetDefinition(FEATURE, skland.id, setOf(fake.id))), adapters.associateBy { it.id })
    private val avatars = AvatarCache(File(application.cacheDir, "official-avatars"))
    private val renderer = StatusWidgetRenderer(avatars)
    internal val scheduler: JobScheduler = application.getSystemService(JobScheduler::class.java)
    // Bumped by every account change so that requests started earlier cannot write stale results.
    internal var accountGeneration = 0L

    private fun bootCount() = Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun owned() = StatusWidgetProvider.owned(application)

    @Synchronized fun preparePin(token: String, config: WidgetInstanceConfig) {
        pinRequest.prepare(token, config, owned().toSet(), SystemClock.elapsedRealtime(), bootCount())
    }

    @Synchronized fun clearPin(token: String? = null, before: Long? = null) = pinRequest.clear(token, before)

    @Synchronized fun configurePin(config: WidgetInstanceConfig, token: String) {
        if (repository.read(config.widgetId) == null) configure(config)
        pinRequest.clear(token)
    }

    private fun recoverPin(id: Int) {
        if (!pinRequest.pending) return
        val owned = owned().associateWith { StatusWidgetProvider.theme(application, it) }
        val configured = owned.keys.filter { repository.read(it) != null }.toSet()
        val config = pinRequest.candidate(owned, configured, SystemClock.elapsedRealtime(), bootCount()) ?: return
        if (config.widgetId != id) return
        // HyperOS may bind the widget without delivering requestPinAppWidget's success callback.
        coordinator.configure(config)
        pinRequest.clear()
    }

    @Synchronized fun read(id: Int, accountIds: Set<String>? = null): WidgetRecord? {
        val record = repository.read(id)
        if (record?.config?.providerId == skland.id &&
            !(accountIds?.contains(record.config.accountId) ?: accounts.list().any { it.id == record.config.accountId })) {
            coordinator.delete(id)
            return null
        }
        return record
    }

    @Synchronized fun configure(config: WidgetInstanceConfig) {
        require(config.providerId == fake.id || !config.simulateFailure)
        if (coordinator.configure(config)) update(config.widgetId)
        else render(config.widgetId, read(config.widgetId))
    }

    @Synchronized fun update(id: Int, force: Boolean = false) {
        if (id !in owned()) return
        storageSafe(id) {
            recoverPin(id)
            val record = read(id)
            when {
                record == null || record.config.providerId == skland.id && accounts.list().none { it.id == record.config.accountId } ->
                    render(id, record)
                record.config.providerId == fake.id -> render(id, coordinator.refresh(id, System.currentTimeMillis()))
                else -> scheduleRefresh(id, record, force)
            }
        }
    }

    // Real requests run only in WidgetRefreshJob; an already pending job is reused unless the refresh is forced.
    private fun scheduleRefresh(id: Int, record: WidgetRecord, force: Boolean) {
        if (!force && scheduler.getPendingJob(id) != null && !WidgetRefreshJob.downloadingAvatars(id)) {
            render(id, record)
            return
        }
        render(id, coordinator.queued(id))
        val job = JobInfo.Builder(id, ComponentName(application, WidgetRefreshJob::class.java))
            .setExtras(PersistableBundle().apply { putInt("widgetId", id) })
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(256 * 1024, 1024)
            .build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            repository.write(record.copy(state = RefreshState.ERROR, failure = RefreshFailure.NETWORK))
            render(id, repository.read(id))
        }
    }

    fun runRefresh(id: Int): Boolean {
        if (id !in owned()) return false
        var stale = false
        try { stale = coordinator.refresh(id, System.currentTimeMillis()) == null }
        catch (_: java.io.IOException) { synchronized(this) { render(id, null, true) } }
        catch (_: IllegalArgumentException) { synchronized(this) { render(id, null, true) } }
        finally {
            synchronized(this) {
                if (id in owned()) storageSafe(id) {
                    val current = read(id)
                    render(id, current)
                    stale = stale && current?.state == RefreshState.LOADING
                }
            }
        }
        return stale
    }

    fun refreshAvatars(id: Int) {
        val original = synchronized(this) { if (id in owned()) read(id) else null } ?: return
        val snapshot = original.snapshot ?: return
        if (original.state != RefreshState.READY) return
        fun stillShown(current: WidgetRecord?) = current?.config?.sameDataBinding(original.config) == true && current.snapshot == snapshot
        var changed = false
        listOfNotNull(snapshot.avatarUrl, trainingAvatar(original)).distinct().forEach { url ->
            if (Thread.currentThread().isInterrupted) return
            if (!synchronized(this) { stillShown(read(id)) }) return
            changed = avatars.prepare(url) || changed
        }
        if (changed) synchronized(this) {
            if (id in owned()) read(id)?.takeIf(::stillShown)?.let { render(id, it) }
        }
    }

    @Synchronized fun redraw(id: Int) {
        if (id !in owned()) return
        storageSafe(id) { render(id, read(id)) }
    }

    // Unreadable widget storage is shown on the widget instead of failing the caller.
    private inline fun storageSafe(id: Int, block: () -> Unit) {
        try { block() }
        catch (_: java.io.IOException) { render(id, null, true) }
        catch (_: IllegalArgumentException) { render(id, null, true) }
    }

    internal fun render(id: Int, record: WidgetRecord?, storageError: Boolean = false) {
        val manager = AppWidgetManager.getInstance(application)
        val options = manager.getAppWidgetOptions(id)
        val editUri = widgetConfigureUri(id)
        if (options.getString("miuiEditUri") != editUri) {
            options.putString("miuiEditUri", editUri)
            manager.updateAppWidgetOptions(id, options)
        }
        manager.updateAppWidget(id, renderer.render(application, id, record, storageError))
        application.contentResolver.notifyChange(WidgetStorageProvider.URI, null)
    }

    @Synchronized fun stopRefresh(id: Int) {
        coordinator.cancel(id)
    }

    @Synchronized fun delete(id: Int) {
        coordinator.delete(id)
        scheduler.cancel(id)
    }

    @Synchronized fun remap(oldIds: IntArray, newIds: IntArray) {
        coordinator.remap(oldIds, newIds)
        oldIds.filter { it !in newIds }.forEach { scheduler.cancel(it) }
    }

    fun listAccounts() = accounts.list()
    // Each choice is paired with the ID of the adapter that listed it, so callers never guess the data source.
    fun characters() = adapters.flatMap { adapter -> adapter.characters.map { adapter.id to it } }

    @Synchronized fun dashboard(accountId: String, characterId: String): RoleData {
        val currentAccounts = accounts.list()
        require(currentAccounts.any { it.id == accountId && it.roles.any { role -> role.uid == characterId } })
        val accountIds = currentAccounts.map { it.id }.toSet()
        val saved = roleData.read(accountId, characterId)
        val cached = owned().toList().mapNotNull { id ->
            read(id, accountIds)?.takeIf { it.config.accountId == accountId && it.config.characterId == characterId }?.snapshot
        }.maxByOrNull { it.fetchedAt }
        return saved.copy(snapshot = listOfNotNull(saved.snapshot, cached).maxByOrNull { it.fetchedAt })
    }

    fun refreshRole(accountId: String, characterId: String) {
        val generation = synchronized(this) {
            dashboard(accountId, characterId)
            accountGeneration
        }
        try { skland.fetchRole(accountId, characterId) }
        catch (failure: DataSourceFailure) {
            synchronized(this) {
                if (generation == accountGeneration) {
                    val previous = dashboard(accountId, characterId)
                    roleData.write(accountId, characterId, previous.copy(failure = failure.reason, retryAt = failure.retryAt))
                }
            }
        }
        synchronized(this) {
            if (generation != accountGeneration) return
            owned().filter { id -> read(id)?.config?.let { it.accountId == accountId && it.characterId == characterId } == true }
                .forEach { update(it, true) }
        }
    }

    fun avatar(url: String?): ByteArray? = avatars.read(url)
    fun prepareAvatar(url: String?) {
        avatars.prepare(url)
    }

    // The preview is shared with the app process; a shared copy avoids keeping two large bitmaps alive.
    fun preview(snapshot: WidgetSnapshot?, theme: WidgetTheme): Bitmap {
        val bitmap = renderer.preview(application, snapshot, theme)
        val shared = try {
            bitmap.asShared()
        } catch (failure: Exception) {
            bitmap.recycle()
            throw failure
        }
        if (shared !== bitmap) bitmap.recycle()
        return shared
    }

    companion object {
        const val FEATURE = "skland.arknights.status"
        private var instance: WidgetRuntime? = null
        @Synchronized fun get(context: Context): WidgetRuntime = instance
            ?: WidgetRuntime(context.applicationContext as Application).also { instance = it }
    }
}
