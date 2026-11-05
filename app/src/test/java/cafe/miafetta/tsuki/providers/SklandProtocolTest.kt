package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.core.TrainingState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// Skland client: request signing, session refresh, rate limits and role parsing.
class SklandProtocolTest : SklandFixtureTest() {
    @Test fun signatureMatchesIndependentPythonVectorAndSignsTheRawQuery() {
        val headers = SklandSigner.headers(cred, "test-sign-key", "/api/v1/game/player/info?uid=000000001", 1_700_000_000)
        assertEquals("a8a4788d99b06ef688f20ca2bf00496d", headers["sign"])
        assertEquals("1700000000", headers["timestamp"])
        assertEquals(cred, headers["cred"])
        assertNotEquals(headers["sign"], SklandSigner.headers(cred, "test-sign-key",
            "/api/v1/game/player/info?uid=000000002", 1_700_000_000)["sign"])
    }

    @Test fun refreshSessionThenReadRolesAndReuseSession() {
        val paths = mutableListOf<String>()
        val client = SklandClient(SklandTransport { path, headers ->
            paths.add(path)
            assertEquals(cred, headers["cred"])
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key"))
            else {
                assertNotNull(headers["sign"])
                response(fixture("binding"))
            }
        }) { now }
        assertEquals(listOf("000000001", "000000002"), client.roles(cred).map { it.uid })
        client.roles(cred)
        assertEquals(1, paths.count { it.endsWith("refresh") })
        assertTrue(client.roles(cred).all { !it.label.contains("其他游戏") })
    }

    @Test fun expiredSignatureRefreshesOnceButInvalidCredStops() {
        var refreshes = 0
        var bindings = 0
        val client = SklandClient(SklandTransport { path, _ ->
            if (path.endsWith("refresh")) {
                refreshes++
                response(JSONObject().put("token", "test-sign-key"))
            }
            else if (bindings++ == 0) SklandResponse(200, "{\"code\":10000}") else response(fixture("binding"))
        }) { now }
        client.roles(cred)
        assertEquals(2, refreshes)
        val rejected = SklandClient(SklandTransport { _, _ -> SklandResponse(401, "secret response") }) { now }
        assertEquals("AUTH_EXPIRED", reason(RefreshFailure.AUTH_EXPIRED) { rejected.roles(cred) }.message)
    }

    @Test fun rateLimitHonorsRetryAfterAndDoesNotIssueAnotherRequest() {
        var requests = 0
        val client = SklandClient(SklandTransport { path, _ ->
            requests++
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key"))
            else SklandResponse(429, "private response", "120")
        }) { now }
        assertEquals(now + 120000, reason(RefreshFailure.RATE_LIMIT) { client.roles(cred) }.retryAt)
        reason(RefreshFailure.RATE_LIMIT) { client.roles(cred) }
        assertEquals(2, requests)
        assertEquals(now + 1000, SklandClient.retryAt("0", now))
        assertEquals(now + 60000, SklandClient.retryAt("invalid", now))
        assertEquals(now + 86400000, SklandClient.retryAt("999999999", now))
        assertEquals(now + 60000, SklandClient.retryAt("Tue, 14 Nov 2023 22:14:20 GMT", now))
    }

    @Test fun missingEnvelopeMalformedJsonAndClockSkewAreNotSuccess() {
        for ((body, expected) in listOf("{}" to RefreshFailure.INVALID_DATA,
            "not-json" to RefreshFailure.INVALID_DATA, "{\"code\":10003}" to RefreshFailure.CLOCK_SKEW,
            "{\"code\":99999,\"message\":\"secret\"}" to RefreshFailure.SERVER)) {
            val client = SklandClient(SklandTransport { _, _ -> SklandResponse(200, body) }) { now }
            assertFalse(reason(expected) { client.roles(cred) }.message!!.contains("secret"))
        }
    }

    @Test fun invalidAndDuplicateRoleIdsAreRejected() {
        reason(RefreshFailure.INVALID_DATA) { SklandClient.parseRoles(JSONObject("{\"list\":[]}")) }
        val data = fixture("binding")
        val bindings = data.getJSONArray("list").getJSONObject(1).getJSONArray("bindingList")
        bindings.getJSONObject(1).put("uid", "000000001")
        reason(RefreshFailure.INVALID_DATA) { SklandClient.parseRoles(data) }
        bindings.getJSONObject(1).put("uid", "1&uid=other")
        reason(RefreshFailure.INVALID_DATA) { SklandClient.parseRoles(data) }
        assertThrows(IllegalArgumentException::class.java) { SklandClient.validateCred("cred\nInjected: value") }
    }

    @Test fun refreshEndpointRateLimitAlsoBlocksRepeatedAuthenticationAttempts() {
        var requests = 0
        val client = SklandClient(SklandTransport { _, _ ->
            requests++
            SklandResponse(429, "", "120")
        }) { now }
        reason(RefreshFailure.RATE_LIMIT) { client.roles(cred) }
        reason(RefreshFailure.RATE_LIMIT) { client.roles(cred) }
        assertEquals(1, requests)
    }

}
