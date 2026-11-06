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

    private fun endfieldRoles(data: org.json.JSONObject) =
        data.getJSONArray("list").getJSONObject(1).getJSONArray("bindingList").getJSONObject(0).getJSONArray("roles")
}
