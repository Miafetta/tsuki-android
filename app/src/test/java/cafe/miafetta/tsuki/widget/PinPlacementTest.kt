package cafe.miafetta.tsuki.widget

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PinPlacementTest {
    @Test fun cancellationAfterStagingClearsTheSelectionWithoutAskingTheLauncher() = runBlocking {
        val staged = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var cleared = false
        val job = launch {
            withStagedPin(
                prepare = {
                    staged.complete(Unit)
                    check(release.await(5, SECONDS))
                    true
                },
                request = { error("Cancelled placement must not open the launcher") },
                cancel = { cleared = true },
            )
        }
        try {
            withTimeout(5000) { staged.await() }
            job.cancel()
        } finally {
            release.countDown()
        }
        job.join()
        assertTrue(cleared)
    }

    @Test fun onlyAnAcceptedRequestKeepsItsSelection() = runBlocking {
        for (accepted in listOf(false, true)) {
            var cleared = false
            assertEquals(accepted, withStagedPin(prepare = { true }, request = { accepted }, cancel = { cleared = true }))
            assertEquals(!accepted, cleared)
        }
    }
}
