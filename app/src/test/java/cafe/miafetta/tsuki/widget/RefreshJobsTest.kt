package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import org.junit.Assert.*
import org.junit.Test

// The one retry after a rate-limited widget refresh (docs/DEVELOPMENT_CONSTRAINTS.md §2).
class RefreshJobsTest {
    private val now = 1_793_956_830_000L
    private val config = WidgetInstanceConfig(7, SklandArknightsAdapter.ID, WidgetRuntime.FEATURE, "account-a", "000000001")

    private fun result(failure: RefreshFailure?, retryAt: Long = 0, state: RefreshState = RefreshState.ERROR) =
        WidgetRecord(config, state = state, failure = failure, retryAt = retryAt)

    @Test fun aRateLimitWithAFutureRetryTimeGetsOneRetry() {
        assertEquals(now + 20_000, RefreshJobs.retryAt(result(RefreshFailure.RATE_LIMIT, now + 20_000), retried = false, now))
        // The retry limited again schedules nothing: one retry, never a loop.
        assertNull(RefreshJobs.retryAt(result(RefreshFailure.RATE_LIMIT, now + 20_000), retried = true, now))
    }

    @Test fun otherResultsGetNoRetry() {
        // A rate limit without a retry time, or one that has passed.
        assertNull(RefreshJobs.retryAt(result(RefreshFailure.RATE_LIMIT), retried = false, now))
        assertNull(RefreshJobs.retryAt(result(RefreshFailure.RATE_LIMIT, now), retried = false, now))
        assertNull(RefreshJobs.retryAt(result(RefreshFailure.RATE_LIMIT, now - 1), retried = false, now))
        // Other failures, a success, a refresh still loading, and a widget without a record.
        assertNull(RefreshJobs.retryAt(result(RefreshFailure.NETWORK, now + 20_000), retried = false, now))
        assertNull(RefreshJobs.retryAt(result(null, state = RefreshState.READY), retried = false, now))
        assertNull(RefreshJobs.retryAt(result(null, state = RefreshState.LOADING), retried = false, now))
        assertNull(RefreshJobs.retryAt(null, retried = false, now))
    }
}
