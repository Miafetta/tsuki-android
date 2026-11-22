package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.stableId
import java.io.File
import javax.crypto.KeyGenerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Endfield card parsing and requests. endfield-card and user-teenager are redacted production responses (2026-11-06).
class EndfieldTest : SklandFixtureTest() {
    @get:Rule val directory = TemporaryFolder()
    private val roleId = "0000000011"
    private val receivedAt = 1_793_956_830_000L

    @Test fun productionCardGivesTheOfficialRealtimeMetricsInThePageOrder() {
        val snapshot = EndfieldParser.snapshot(fixture("endfield-card"), roleId, receivedAt)
        assertEquals("测试管理员", snapshot.title)
        assertEquals(listOf("理智", "活跃度", "每周事务", "通行证", "蚀像寻遗"), snapshot.metrics.map { it.label })
        assertEquals(listOf("125/125", "0/100", "0/10", "0/60", "0/200000"), snapshot.metrics.map { it.amount() })
        assertEquals(listOf("endfield.sanity", "endfield.daily_activation", "endfield.weekly_mission", "endfield.pass_level",
            "endfield.seek_suspicion"), snapshot.metrics.map { it.stableId(arknightsSource = false) })
        assertEquals(0L, snapshot.metrics.first().recoveryAt)
        assertEquals(1_793_956_828_000L, snapshot.dataAt)
        assertTrue(snapshot.notes.isEmpty())
    }

    @Test fun pageOrderFollowsTheUserSettingAndMissingModulesOnlyAddANote() {
        val data = fixture("endfield-card")
        val detail = data.getJSONObject("detail")
        detail.getJSONObject("config").put("realtimeModules", JSONArray()
            .put(JSONObject().put("id", "REALTIME_MODULE_SEEK_SUSPICION")).put(JSONObject().put("id", "UNKNOWN")))
        detail.remove("weeklyMission")
        val snapshot = EndfieldParser.snapshot(data, roleId, receivedAt)
        assertEquals(listOf("理智", "蚀像寻遗", "活跃度", "通行证"), snapshot.metrics.map { it.label })
        assertEquals(listOf("每周事务数据缺失"), snapshot.notes)
        detail.getJSONObject("seekSuspicion").put("count", 250_000)
        val clamped = EndfieldParser.snapshot(data, roleId, receivedAt)
        assertEquals("200000/200000", clamped.metrics.first { it.label == "蚀像寻遗" }.amount())
    }

    @Test fun sanityRecoveryUsesTheFullTimeAndOtherRolesAreRejected() {
        val data = fixture("endfield-card")
        val dungeon = data.getJSONObject("detail").getJSONObject("dungeon")
        dungeon.put("curStamina", "100").put("maxTs", "1793960428")
        val sanity = EndfieldParser.snapshot(data, roleId, receivedAt).metrics.first()
        assertEquals(receivedAt + 3_600_000, sanity.recoveryAt)
        dungeon.put("maxTs", "0")
        assertEquals(listOf("理智恢复时间未知"), EndfieldParser.snapshot(data, roleId, receivedAt).notes)
        reason(RefreshFailure.INVALID_DATA) { EndfieldParser.snapshot(data, "0000000012", receivedAt) }
        dungeon.remove("maxStamina")
        reason(RefreshFailure.INVALID_DATA) { EndfieldParser.snapshot(data, roleId, receivedAt) }
    }

    @Test fun cardRequestCarriesTheUserIdFromTeenagerAndReusesIt() {
        val paths = mutableListOf<String>()
        val client = SklandClient(SklandTransport { path, _ ->
            paths += path
            when {
                path.endsWith("refresh") -> response(JSONObject().put("token", "test-sign-key"))
                path.startsWith("/api/v1/user/teenager") -> response(fixture("user-teenager"))
                else -> response(fixture("endfield-card"))
            }
        }) { now }
        repeat(2) { client.endfieldCard(cred, "1", roleId) }
        assertEquals(1, paths.count { it.startsWith("/api/v1/user/teenager") })
        assertEquals("/web/v1/game/endfield/card/detail?roleId=0000000011&serverId=1&userId=1000001", paths.last())
        assertThrows(IllegalArgumentException::class.java) { client.endfieldCard(cred, "1&x=2", roleId) }
    }

    @Test fun adapterListsOnlyEndfieldRolesAndFetchesTheCard() {
        val accounts = FileAccountRepository(File(directory.root, "accounts.bin"),
            CredentialCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().let { key -> { key } }))
        val endfield = SklandRole(SklandRole.endfieldId("1", roleId), "测试管理员", SklandGame.ENDFIELD, "官服")
        accounts.save(SklandAccount("account-a", "", cred, listOf(SklandRole("000000001", "测试博士 · 官服 · 000000001"), endfield)))
        val saved = mutableListOf<String>()
        val adapter = SklandEndfieldAdapter(accounts, SklandClient(SklandTransport { path, _ ->
            when {
                path.endsWith("refresh") -> response(JSONObject().put("token", "test-sign-key"))
                path.startsWith("/api/v1/user/teenager") -> response(fixture("user-teenager"))
                else -> response(fixture("endfield-card"))
            }
        }) { now }, onSnapshot = { account, role, _ -> saved += "$account/$role" }) { receivedAt }
        assertEquals(SklandGame.ENDFIELD.providerId, adapter.id)
        assertEquals(listOf("1~0000000011"), adapter.characters.map { it.characterId })
        assertEquals("测试管理员", adapter.fetchRole("account-a", "1~0000000011").title)
        assertEquals(listOf("account-a/1~0000000011"), saved)
        val failure = assertThrows(cafe.miafetta.tsuki.core.DataSourceFailure::class.java) {
            adapter.fetchRole("account-a", "000000001")
        }
        assertEquals(RefreshFailure.AUTH_EXPIRED, failure.reason)
    }
}
