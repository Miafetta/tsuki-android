package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.RefreshFailure
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// The Skland account nickname offered as the optional account name, and the request paths the client may use.
class SklandNicknameTest : SklandFixtureTest() {
    @Test fun nicknameIsReadFromTheSignedProfileAndNothingElseOfItIsReturned() {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val client = SklandClient(SklandTransport { path, headers ->
            requests.add(path to headers)
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key")) else response(fixture("user"))
        }) { now }
        assertEquals("测试博士#0001", client.nickname(cred))
        assertEquals(listOf("/api/v1/auth/refresh", "/api/v1/user"), requests.map { it.first })
        val signed = requests.last().second
        assertEquals(SklandSigner.headers(cred, "test-sign-key", "/api/v1/user", now / 1000 - 1), signed)
        // The profile's application nickname and the game roles are never taken for the account name.
        assertNotEquals("测试申请昵称", client.nickname(cred))
    }

    @Test fun missingBlankOrMalformedNicknamesLeaveTheNameBlank() {
        assertNull(SklandClient.parseNickname(JSONObject()))
        assertNull(SklandClient.parseNickname(JSONObject("{\"user\":{}}")))
        assertNull(SklandClient.parseNickname(JSONObject("{\"user\":{\"nickname\":null}}")))
        assertNull(SklandClient.parseNickname(JSONObject("{\"user\":{\"nickname\":12345}}")))
        assertNull(SklandClient.parseNickname(JSONObject("{\"user\":{\"nickname\":\" \\n\\t \"}}")))
        assertNull(SklandClient.parseNickname(JSONObject("{\"user\":\"测试博士\"}")))
        assertEquals("测试管理员#0002", SklandClient.parseNickname(JSONObject("{\"user\":{\"nickname\":\" 测试管理员\\n#0002 \"}}")))
        val long = "测".repeat(SklandClient.NAME_LIMIT + 5)
        assertEquals(SklandClient.NAME_LIMIT, SklandClient.parseNickname(JSONObject().put("user",
            JSONObject().put("nickname", long)))?.length)
    }

    @Test fun failedNicknameReadDoesNotStopTheAccountFromBeingVerified() {
        val client = SklandClient(SklandTransport { path, _ ->
            when {
                path.endsWith("refresh") -> response(JSONObject().put("token", "test-sign-key"))
                path == "/api/v1/user" -> SklandResponse(200, "{\"code\":10001,\"message\":\"private\"}")
                else -> response(fixture("binding"))
            }
        }) { now }
        assertFalse(reason(RefreshFailure.SERVER) { client.nickname(cred) }.message!!.contains("private"))
        assertEquals(listOf("000000001", "000000002"), client.roles(cred).map { it.uid })
        val empty = SklandClient(SklandTransport { path, _ ->
            if (path.endsWith("refresh")) response(JSONObject().put("token", "test-sign-key")) else response(JSONObject())
        }) { now }
        assertNull(empty.nickname(cred))
    }

    @Test fun transportAllowsOnlyTheListedPaths() {
        assertEquals(setOf("/api/v1/auth/refresh", "/api/v1/game/player/binding", "/api/v1/game/player/info", "/api/v1/user",
            "/api/v1/user/teenager", "/web/v1/game/endfield/card/detail"), SklandClient.PATHS)
        // Rejected before any connection is opened.
        for (path in listOf("/api/v1/user/me", "/api/v1/user/info", "/api/v1/users", "/api/v1/user/", "/web/v1/user")) {
            assertThrows(IllegalArgumentException::class.java) { SklandHttpsTransport().request(path, emptyMap()) }
        }
    }
}
