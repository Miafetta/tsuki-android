package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceAdapter
import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.WidgetCharacter
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetSnapshot

// One Skland game's roles on the stored accounts. Shared by every Skland game: account lookup, the 20-second reuse of
// a role's last success, the check that a late response still belongs to the account, and rate-limit backoff.
internal abstract class SklandRoleAdapter(private val game: SklandGame, private val accounts: AccountRepository,
    private val onSnapshot: (String, String, WidgetSnapshot) -> Unit, protected val clock: () -> Long) : DataSourceAdapter {
    override val id = game.providerId
    override val characters: List<WidgetCharacter>
        get() = accounts.list().flatMap { account ->
            account.roles.filter { it.game == game }
                .map { WidgetCharacter(account.id, it.uid, "${account.name} · ${it.label}".take(120)) }
        }
    private val lastFetch = mutableMapOf<String, Long>()
    private val snapshots = mutableMapOf<String, WidgetSnapshot>()

    // One network request for the role; runs outside every lock.
    protected abstract fun request(cred: String, characterId: String): WidgetSnapshot

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
        if (account.roles.none { it.matches(characterId) }) throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
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
            val snapshot = request(account.cred, characterId)
            synchronized(accounts) {
                val stillBound = accounts.list().any {
                    it.id == account.id && it.cred == account.cred && it.roles.any { role -> role.matches(characterId) }
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

    private fun SklandRole.matches(characterId: String) = game == this@SklandRoleAdapter.game && uid == characterId
}
