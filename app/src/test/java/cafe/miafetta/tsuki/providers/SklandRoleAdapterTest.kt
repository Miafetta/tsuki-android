package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.FileWidgetRepository
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.WidgetDefinition
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetUpdateCoordinator
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import javax.crypto.KeyGenerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// The 20-second window of SklandRoleAdapter: a success inside it is reused, a failure inside it is reported again, an
// interrupted request leaves no result, and none sends a request. The games share this logic, so every scenario runs on
// each game's adapter.
class SklandRoleAdapterTest : SklandFixtureTest() {
    @get:Rule val directory = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun failedRefreshInsideTheWindowStaysFailedInsteadOfReturningAnOlderSuccess() = eachGame {
        val first = refresh()!!
        assertEquals(RefreshState.READY, first.state)
        time += 300_000
        reply = { throw DataSourceFailure(RefreshFailure.NETWORK) }
        val failed = refresh()!!
        assertEquals(RefreshFailure.NETWORK, failed.failure)
        time += 1_000
        val retried = refresh()!!
        assertEquals(RefreshState.ERROR, retried.state)
        assertEquals(RefreshFailure.NETWORK, retried.failure)
        assertEquals(first.snapshot, retried.snapshot)
        assertEquals(failed, retried)
        assertEquals(retried, widgets.read(10))
        // The overview's manual refresh calls fetchRole directly.
        assertEquals(0L, reason(RefreshFailure.NETWORK) { fetch() }.retryAt)
        assertEquals(2, requests)
    }

    @Test fun successInsideTheWindowIsReusedAndTheNextRequestWaitsForItsEnd() = eachGame {
        val first = fetch()
        time += 19_999
        assertSame(first, fetch())
        assertEquals(first, refresh()!!.snapshot)
        assertEquals(1, requests)
        time += 1
        assertEquals(time, fetch().fetchedAt)
        assertEquals(2, requests)
    }

    @Test fun failureIsRepeatedInsideTheWindowAndRequestedAgainAfterIt() = eachGame {
        reply = { SklandResponse(500, "") }
        reason(RefreshFailure.SERVER) { fetch() }
        time += 19_999
        // Without any earlier success the failure is still reported as itself, not as a rate limit.
        assertEquals(0L, reason(RefreshFailure.SERVER) { fetch() }.retryAt)
        assertEquals(1, requests)
        time += 1
        reply = sample
        assertEquals(RefreshState.READY, refresh()!!.state)
        assertEquals(2, requests)
    }

    @Test fun otherExceptionsCountAsNetworkFailuresInsideTheWindow() = eachGame {
        val first = refresh()!!
        time += 300_000
        reply = { throw IOException("connection reset") }
        assertEquals(RefreshFailure.NETWORK, refresh()!!.failure)
        time += 1_000
        val retried = refresh()!!
        assertEquals(RefreshState.ERROR, retried.state)
        assertEquals(RefreshFailure.NETWORK, retried.failure)
        assertEquals(first.snapshot, retried.snapshot)
        assertEquals(0L, reason(RefreshFailure.NETWORK) { fetch() }.retryAt)
        assertEquals(2, requests)
    }

    @Test fun requestInFlightReportsRateLimitEvenWhenAnOlderSuccessExists() = eachGame {
        fetch()
        time += 300_000
        val started = time
        val during = mutableListOf<DataSourceFailure>()
        reply = {
            time += 1_000
            during += assertThrows(DataSourceFailure::class.java) { fetch() }
            sample()
        }
        fetch()
        assertEquals(RefreshFailure.RATE_LIMIT, during.single().reason)
        assertEquals(started + 20_000, during.single().retryAt)
        assertEquals(2, requests)
    }

    @Test fun rateLimitedAttemptBlocksUntilTheLaterOfTheServerTimeAndTheWindowEnd() = eachGame {
        fetch()
        time += 300_000
        val started = time
        reply = { SklandResponse(429, "", "1") }
        assertEquals(started + 1_000, reason(RefreshFailure.RATE_LIMIT) { fetch() }.retryAt)
        assertEquals(started + 1_000, accounts.list().single().retryAt)
        time += 2_000
        assertEquals(started + 20_000, reason(RefreshFailure.RATE_LIMIT) { fetch() }.retryAt)
        assertEquals(started + 1_000, accounts.list().single().retryAt)
        assertEquals(2, requests)
    }

    @Test fun forgettingAnAccountDropsItsReusedSuccessAndItsRememberedFailure() = eachGame {
        fetch()
        adapter.forget(ACCOUNT)
        fetch()
        adapter.forget()
        reply = { SklandResponse(500, "") }
        reason(RefreshFailure.SERVER) { fetch() }
        adapter.forget(ACCOUNT)
        reply = sample
        fetch()
        assertEquals(4, requests)
    }

    @Test fun lateFailureOfAnOlderRequestDoesNotHideTheNewerSuccess() = eachGame {
        val newer = mutableListOf<WidgetSnapshot>()
        reply = {
            time += 21_000
            reply = sample
            newer += fetch()
            throw DataSourceFailure(RefreshFailure.NETWORK)
        }
        reason(RefreshFailure.NETWORK) { fetch() }
        assertSame(newer.single(), fetch())
        assertEquals(2, requests)
    }

    // A stopped refresh job interrupts its thread, and SklandHttpsTransport reports that as NETWORK with the flag set.
    @Test fun interruptedRequestIsAnsweredAsARateLimitInsteadOfANetworkFailure() = eachGame {
        val first = refresh()!!
        time += 300_000
        val started = time
        reply = {
            // WidgetRefreshJob.onStopJob drops the coordinator request before interrupting the thread.
            cancel()
            Thread.currentThread().interrupt()
            throw DataSourceFailure(RefreshFailure.NETWORK)
        }
        assertNull(refresh())
        assertTrue(Thread.interrupted())
        time += 1_000
        val retried = refresh()!!
        assertEquals(RefreshState.ERROR, retried.state)
        assertEquals(RefreshFailure.RATE_LIMIT, retried.failure)
        assertEquals(started + 20_000, retried.retryAt)
        assertEquals(first.snapshot, retried.snapshot)
        assertEquals(retried, widgets.read(10))
        assertEquals(started + 20_000, reason(RefreshFailure.RATE_LIMIT) { fetch() }.retryAt)
        assertEquals(2, requests)
    }

    @Test fun interruptedRequestBlocksTheRoleOnlyUntilItsWindowEnds() = eachGame {
        fetch()
        time += 300_000
        val started = time
        reply = {
            Thread.currentThread().interrupt()
            throw InterruptedIOException()
        }
        assertThrows(InterruptedIOException::class.java) { fetch() }
        assertTrue(Thread.interrupted())
        time += 19_999
        assertEquals(started + 20_000, reason(RefreshFailure.RATE_LIMIT) { fetch() }.retryAt)
        assertEquals(2, requests)
        time += 1
        reply = sample
        val renewed = refresh()!!
        assertEquals(RefreshState.READY, renewed.state)
        assertEquals(time, renewed.snapshot!!.fetchedAt)
        assertEquals(3, requests)
    }

    // SocketTimeoutException is an InterruptedIOException, yet a timeout is a real network failure.
    @Test fun timeoutIsStillRepeatedAsANetworkFailureInsideTheWindow() = eachGame {
        val first = refresh()!!
        time += 300_000
        reply = { throw SocketTimeoutException("timeout") }
        assertEquals(RefreshFailure.NETWORK, refresh()!!.failure)
        assertFalse(Thread.currentThread().isInterrupted)
        time += 1_000
        val retried = refresh()!!
        assertEquals(RefreshFailure.NETWORK, retried.failure)
        assertEquals(first.snapshot, retried.snapshot)
        assertEquals(0L, reason(RefreshFailure.NETWORK) { fetch() }.retryAt)
        assertEquals(2, requests)
    }

    private fun eachGame(scenario: Game.() -> Unit) = SklandGame.entries.forEach { game ->
        try { Game(game).scenario() }
        catch (failure: AssertionError) { throw AssertionError("${game.name}: ${failure.message}", failure) }
        // A failed assertion must not leave the interrupt flag to the next scenario.
        finally { Thread.interrupted() }
    }

    // One account with a role of [game], its adapter over a scripted transport, and a widget bound to the role.
    private inner class Game(game: SklandGame) {
        var time = now
        // Requests for the role's data; signing and user ID requests are not counted.
        var requests = 0
        val sample: () -> SklandResponse = { response(fixture(if (game == SklandGame.ENDFIELD) "endfield-card" else "player")) }
        var reply = sample
        val accounts = FileAccountRepository(File(directory.newFolder(), "accounts.bin"), CredentialCipher { key })
        private val client = SklandClient(SklandTransport { path, _ ->
            when {
                path.endsWith("refresh") -> response(JSONObject().put("token", "test-sign-key"))
                path.startsWith("/api/v1/user/teenager") -> response(fixture("user-teenager"))
                else -> {
                    requests++
                    reply()
                }
            }
        }) { time }
        val adapter: SklandRoleAdapter = when (game) {
            SklandGame.ARKNIGHTS -> SklandArknightsAdapter(accounts, client) { time }
            SklandGame.ENDFIELD -> SklandEndfieldAdapter(accounts, client) { time }
        }
        private val role = when (game) {
            SklandGame.ARKNIGHTS -> SklandRole("000000001", "测试博士")
            SklandGame.ENDFIELD -> SklandRole(SklandRole.endfieldId("1", "0000000011"), "测试管理员", game, "官服")
        }
        private val feature = if (game == SklandGame.ENDFIELD) "skland.endfield.status" else "skland.arknights.status"
        val widgets = FileWidgetRepository(directory.newFolder())
        private val coordinator = WidgetUpdateCoordinator(widgets, mapOf(feature to WidgetDefinition(feature, adapter.id)),
            mapOf(adapter.id to adapter))

        init {
            accounts.save(SklandAccount(ACCOUNT, "", cred, listOf(role)))
            coordinator.configure(WidgetInstanceConfig(10, adapter.id, feature, ACCOUNT, role.uid))
        }

        fun fetch() = adapter.fetchRole(ACCOUNT, role.uid)
        fun refresh() = coordinator.refresh(10, time)
        fun cancel() = coordinator.cancel(10)
    }

    private companion object { const val ACCOUNT = "account-a" }
}
