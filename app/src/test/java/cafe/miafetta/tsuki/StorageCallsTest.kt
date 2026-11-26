package cafe.miafetta.tsuki

import cafe.miafetta.tsuki.core.AccountProblem
import cafe.miafetta.tsuki.core.RefreshFailure
import org.junit.Assert.*
import org.junit.Test

class StorageCallsTest {
    // A refused account is reported in its own words, not as "无法读取小部件设置，请重试".
    @Test fun everyAccountProblemHasItsOwnTextAndOtherFailuresKeepTheirs() {
        val texts = AccountProblem.entries.map { requireNotNull(accountProblemText(it.name)) { it.name } }
        assertEquals(texts.size, texts.toSet().size)
        assertTrue(texts.first().contains("10"))
        for (failure in RefreshFailure.entries.map { it.name } + listOf(null, "", "UNKNOWN")) {
            assertNull(accountProblemText(failure))
        }
    }
}
