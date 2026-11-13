package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceAdapter
import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.WidgetCharacter
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetSnapshot

// One Skland game's roles on the stored accounts. Shared by every Skland game: account lookup, the 20-second window in
// which a role's last request is answered again instead of repeated, the check that a late response still belongs to
// the account, and rate-limit backoff.
internal abstract class SklandRoleAdapter(private val game: SklandGame, private val accounts: AccountRepository,
    private val onSnapshot: (String, String, WidgetSnapshot) -> Unit, protected val clock: () -> Long) : DataSourceAdapter {
    override val id = game.providerId
    override val characters: List<WidgetCharacter>
        get() = accounts.list().flatMap { account ->
            account.roles.filter { it.game == game }
                .map { WidgetCharacter(account.id, it.uid, "${account.name} · ${it.label}".take(120)) }
        }
    // Only the latest request of each role. A success older than that request is never kept: returning it after a
    // failed refresh would make the caller record the refresh as successful.
    private val attempts = mutableMapOf<String, Attempt>()

    // One network request for the role; runs outside every lock.
    protected abstract fun request(cred: String, characterId: String): WidgetSnapshot

    fun forget(accountId: String? = null) = synchronized(attempts) {
        if (accountId == null) attempts.clear()
        else attempts.keys.removeAll { it.startsWith("$accountId/") }
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
        val attempt = Attempt(current)
        synchronized(attempts) {
            attempts[key]?.takeIf { current < it.startedAt + WINDOW_MILLIS }?.let { return it.outcome() }
            attempts[key] = attempt
        }
        try {
            val snapshot = request(account.cred, characterId)
            synchronized(accounts) {
                val stillBound = accounts.list().any {
                    it.id == account.id && it.cred == account.cred && it.roles.any { role -> role.matches(characterId) }
                }
                if (!stillBound) throw DataSourceFailure(RefreshFailure.AUTH_EXPIRED)
                synchronized(attempts) { attempt.snapshot = snapshot }
                onSnapshot(account.id, characterId, snapshot)
            }
            return snapshot
        } catch (failure: DataSourceFailure) {
            if (failure.reason != RefreshFailure.NETWORK || !cancelled()) synchronized(attempts) {
                attempt.failure = failure.reason
                attempt.retryAt = failure.retryAt
            }
            if (failure.reason == RefreshFailure.RATE_LIMIT) {
                // Do not resurrect credentials if logout ran while the request was in flight.
                synchronized(accounts) {
                    accounts.list().singleOrNull { it.id == account.id && it.cred == account.cred }?.let { existing ->
                        accounts.save(SklandAccount(existing.id, existing.name, existing.cred, existing.roles, failure.retryAt))
                    }
                }
            }
            throw failure
        } catch (failure: Exception) {
            // WidgetUpdateCoordinator records any other exception as a network failure; a repeat must match it.
            if (!cancelled()) synchronized(attempts) { attempt.failure = RefreshFailure.NETWORK }
            throw failure
        }
    }

    // A stopped refresh job interrupts its thread (SklandHttpsTransport keeps the flag set). The request then ended
    // without a result, so callers inside the window get a rate limit rather than a network error that never happened.
    // isInterrupted leaves the flag for the caller's executor to clear.
    private fun cancelled() = Thread.currentThread().isInterrupted

    private fun SklandRole.matches(characterId: String) = game == this@SklandRoleAdapter.game && uid == characterId

    // One request for a role: when it started and, once it has ended, its result. The results are guarded by [attempts].
    private class Attempt(val startedAt: Long) {
        var snapshot: WidgetSnapshot? = null
        var failure: RefreshFailure? = null
        var retryAt = 0L

        // What a caller inside the window gets instead of a second request: the success, or the failure as it was.
        fun outcome(): WidgetSnapshot {
            snapshot?.let { return it }
            // A request that is still running or was cancelled counts as a rate limit, which lasts at least until the
            // window ends.
            val reason = failure ?: RefreshFailure.RATE_LIMIT
            throw DataSourceFailure(reason,
                if (reason == RefreshFailure.RATE_LIMIT) maxOf(retryAt, startedAt + WINDOW_MILLIS) else retryAt)
        }
    }

    private companion object { const val WINDOW_MILLIS = 20_000L }
}
