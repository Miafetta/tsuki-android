package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.core.TrainingState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// Shared fixtures and helpers for the classes split out of the former SklandProtocolTest.
abstract class SklandFixtureTest {
    protected val cred = "test-credential-0001"
    protected val now = 1_700_000_000_000L
    protected fun fixture(name: String) = JSONObject(requireNotNull(javaClass.getResource("/skland/$name-redacted.json"))
        .readText())
    internal fun response(data: JSONObject) = SklandResponse(200, JSONObject().put("code", 0).put("data", data).toString())
    protected fun reason(expected: RefreshFailure, block: () -> Unit): DataSourceFailure {
        val failure = assertThrows(DataSourceFailure::class.java, block)
        assertEquals(expected, failure.reason)
        return failure
    }

}
