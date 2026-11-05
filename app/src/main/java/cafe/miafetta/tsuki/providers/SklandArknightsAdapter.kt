package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceAdapter
import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.WidgetCharacter
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetSnapshot

internal class SklandArknightsAdapter(private val accounts: AccountRepository, private val client: SklandClient,
    private val onSnapshot: (String, String, WidgetSnapshot) -> Unit = { _, _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis) : DataSourceAdapter {
    override val id = ID
    override val characters: List<WidgetCharacter>
        get() = accounts.list().flatMap { account ->
            account.roles.map { WidgetCharacter(account.id, it.uid, "${account.name} · ${it.label}".take(120)) }
        }
    private val lastFetch = mutableMapOf<String, Long>()
    private val snapshots = mutableMapOf<String, WidgetSnapshot>()

    fun forget(accountId: String? = null) = synchronized(lastFetch) {
        if (accountId == null) {
            lastFetch.clear()
            snapshots.clear()
        } else {
            lastFetch.keys.removeAll { it.startsWith("$accountId/") }
            snapshots.keys.removeAll { it.startsWith("$accountId/") }
        }
    }

    override fun fetch(config: WidgetInstanceConfig, now: Long): WidgetSnapshot =
        fetchRole(config.accountId, config.characterId)

    fun fetchRole(accountId: String, characterId: String): WidgetSnapshot {
        val account = accounts.list().singleOrNull { it.id == accountId }
            ?: throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
        if (account.roles.none { it.uid == characterId }) throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
        val current = clock()
        if (account.retryAt > current) throw DataSourceFailure(RefreshFailure.RATE_LIMIT, account.retryAt)
        val key = "${account.id}/$characterId"
        synchronized(lastFetch) {
            val retryAt = (lastFetch[key] ?: 0) + 20_000
            if (lastFetch.containsKey(key) && current < retryAt) {
                snapshots[key]?.let { return it }
                throw DataSourceFailure(RefreshFailure.RATE_LIMIT, retryAt)
            }
            lastFetch[key] = current
        }
        try {
            val snapshot = ArknightsParser.snapshot(client.player(account.cred, characterId), characterId, clock())
            synchronized(accounts) {
                val stillBound = accounts.list().any {
                    it.id == account.id && it.cred == account.cred && it.roles.any { role -> role.uid == characterId }
                }
                if (!stillBound) throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
                synchronized(lastFetch) { snapshots[key] = snapshot }
                onSnapshot(account.id, characterId, snapshot)
            }
            return snapshot
        } catch (failure: DataSourceFailure) {
            if (failure.reason == RefreshFailure.RATE_LIMIT) {
                // Do not resurrect credentials if logout ran while the request was in flight.
                synchronized(accounts) {
                    accounts.list().singleOrNull { it.id == account.id && it.cred == account.cred }?.let { existing ->
                        accounts.save(SklandAccount(existing.id, existing.name, existing.cred, existing.roles, failure.retryAt))
                    }
                }
            }
            throw failure
        }
    }

    companion object { const val ID = "skland" }
}
