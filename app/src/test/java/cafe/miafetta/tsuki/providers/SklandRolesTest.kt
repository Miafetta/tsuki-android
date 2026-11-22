package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.RefreshFailure
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

// Binding list across games. binding-live is a redacted production response (2026-11-06); binding-games adds the edge
// cases one account cannot show.
class SklandRolesTest : SklandFixtureTest() {
    @Test fun productionBindingListsBothGames() {
        val roles = SklandClient.parseRoles(fixture("binding-live"))
        assertEquals(listOf("skland/000000001", "skland.endfield/1~0000000011"), roles.map { it.key })
        assertEquals("官服", roles.last().server)
        assertEquals("测试管理员", roles.last().label)
    }

    @Test fun bindingListKeepsEachGameAndSkipsDeletedBindingsAndUnknownGames() {
        val roles = SklandClient.parseRoles(fixture("binding-games"))
        assertEquals(listOf("skland/000000001", "skland.endfield/1~000000011", "skland.endfield/2~000000001",
            "skland.endfield/3~000000013"), roles.map { it.key })
        val endfield = roles.filter { it.game == SklandGame.ENDFIELD }
        assertEquals(listOf("测试管理员", "测试管理员二", "无渠道角色"), endfield.map { it.label })
        // The channel names the server; without one the English region is the fallback.
        assertEquals(listOf("官服", "官服", "China"), endfield.map { it.server })
        assertTrue(roles.none { it.label.contains("已删除") || it.label.contains("其他游戏") })
        // The Arknights label keeps the legacy delimited form that installed versions parse.
        assertEquals("测试博士 · 官服 · 000000001", roles.first().label)
    }

    @Test fun anAccountWithOnlyEndfieldRolesIsValid() {
        val data = fixture("binding-games")
        val apps = data.getJSONArray("list")
        data.put("list", JSONArray().put(apps.getJSONObject(1)))
        assertEquals(setOf(SklandGame.ENDFIELD), SklandClient.parseRoles(data).map { it.game }.toSet())
    }

    @Test fun endfieldBindingWithoutRolesAddsNothing() {
        // The older fixture lists an Endfield binding without roles; only the Arknights roles remain.
        assertEquals(listOf("000000001", "000000002"), SklandClient.parseRoles(fixture("binding")).map { it.uid })
    }

    @Test fun malformedOrDuplicateEndfieldRolesAreRejected() {
        for (bad in listOf("1/../2", "", "1~2")) {
            val data = fixture("binding-games")
            endfieldRoles(data).getJSONObject(0).put("serverId", bad)
            reason(RefreshFailure.INVALID_DATA) { SklandClient.parseRoles(data) }
        }
        val duplicate = fixture("binding-games")
        endfieldRoles(duplicate).getJSONObject(1).put("serverId", "1").put("roleId", "000000011")
        reason(RefreshFailure.INVALID_DATA) { SklandClient.parseRoles(duplicate) }
    }

    @Test fun roleShapeDependsOnTheGame() {
        assertThrows(IllegalArgumentException::class.java) { SklandRole("1~2", "测试") }
        assertThrows(IllegalArgumentException::class.java) { SklandRole("1~2", "测试", SklandGame.ENDFIELD) }
        assertThrows(IllegalArgumentException::class.java) { SklandRole("12", "测试", SklandGame.ENDFIELD, "官服") }
        assertEquals("skland.endfield/1~2", SklandRole("1~2", "测试", SklandGame.ENDFIELD, "官服").key)
        assertEquals(SklandGame.ARKNIGHTS, SklandGame.fromProvider(SklandArknightsAdapter.ID))
        assertNull(SklandGame.fromAppCode("unknown"))
    }

    @Test fun endfieldRoleTagIsFilledFromCompactGameCardsWithAppHeaders() {
        val seen = mutableListOf<Pair<String, Map<String, String>>>()
        val client = SklandClient(SklandTransport { path, headers ->
            seen += path to headers
            when {
                path.endsWith("refresh") -> response(org.json.JSONObject().put("token", "test-sign-key"))
                path == "/api/v1/game/player/binding" -> response(fixture("binding-live"))
                path == "/api/v1/user/teenager" -> response(fixture("user-teenager"))
                path.startsWith("/api/v1/game/cards") -> response(fixture("cards"))
                else -> error("Unexpected path: $path")
            }
        }) { now }
        val roles = client.roles(cred)
        assertEquals("测试管理员#0002", roles.single { it.game == SklandGame.ENDFIELD }.label)
        assertEquals("测试博士 · 官服 · 000000001", roles.single { it.game == SklandGame.ARKNIGHTS }.label)
        val (cardsPath, cardsHeaders) = seen.last()
        assertEquals("/api/v1/game/cards?uid=1000001", cardsPath)
        assertEquals("1", cardsHeaders["platform"])
        assertEquals("2.5.0", cardsHeaders["vName"])
        assertEquals(SklandSigner.headers(cred, "test-sign-key", cardsPath, now / 1000 - 1, "1", "2.5.0"), cardsHeaders)
        assertNotEquals(SklandSigner.headers(cred, "test-sign-key", cardsPath, now / 1000 - 1)["sign"], cardsHeaders["sign"])
        // A second roles read reuses the cached userId instead of calling /user/teenager again.
        client.roles(cred)
        assertEquals(1, seen.count { it.first == "/api/v1/user/teenager" })
    }

    @Test fun endfieldCardFailureOrMismatchNeverBlocksBinding() {
        val client = SklandClient(SklandTransport { path, _ ->
            when {
                path.endsWith("refresh") -> response(org.json.JSONObject().put("token", "test-sign-key"))
                path == "/api/v1/game/player/binding" -> response(fixture("binding-live"))
                path == "/api/v1/user/teenager" -> response(fixture("user-teenager"))
                else -> SklandResponse(500, "")
            }
        }) { now }
        assertEquals("测试管理员", client.roles(cred).single { it.game == SklandGame.ENDFIELD }.label)

        // Arknights-only accounts do not request /user/teenager or /game/cards.
        val paths = mutableListOf<String>()
        val akOnly = SklandClient(SklandTransport { path, _ ->
            paths += path
            if (path.endsWith("refresh")) response(org.json.JSONObject().put("token", "test-sign-key"))
            else response(fixture("binding"))
        }) { now }
        akOnly.roles(cred)
        assertTrue(paths.none { it.contains("/game/cards") || it.contains("/user/teenager") })
    }

    @Test fun endfieldCardDisambiguatesByChannelAndDefaultAndSkipsAmbiguousOrInvalidTags() {
        val binding = fixture("binding-games")
        // Give both roles on channel 1 and channel 2 the same nickname; channelId 1 selects channel 1's default role.
        endfieldRoles(binding).getJSONObject(1).put("nickname", "测试管理员")
        val roles = SklandClient.parseRoles(binding)
        val enriched = SklandRoles.applyEndfieldCards(roles, binding, fixture("cards"))
        assertEquals("测试管理员#0002", enriched.first { it.uid == "1~000000011" }.label)
        assertEquals("测试管理员", enriched.first { it.uid == "2~000000001" }.label)

        // Without isDefault or unique match on the same channel, ambiguous roles stay unchanged.
        endfieldRoles(binding).getJSONObject(0).put("isDefault", false)
        assertEquals(roles, SklandRoles.applyEndfieldCards(roles, binding, fixture("cards")))

        // Malformed tags (missing digits, mismatched base name, too long) are ignored.
        for (bad in listOf("测试管理员", "测试管理员#abcd", "其他人#0002", "测".repeat(38) + "#0002")) {
            val cards = fixture("cards")
            cards.getJSONArray("list").getJSONObject(1).getJSONObject("endfield").put("name", bad)
            assertEquals(roles, SklandRoles.applyEndfieldCards(roles, binding, cards))
        }
    }

    private fun endfieldRoles(data: org.json.JSONObject) =
        data.getJSONArray("list").getJSONObject(1).getJSONArray("bindingList").getJSONObject(0).getJSONArray("roles")
}
