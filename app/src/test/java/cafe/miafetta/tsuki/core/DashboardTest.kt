package cafe.miafetta.tsuki.core

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import cafe.miafetta.tsuki.providers.ArknightsParser

class DashboardTest {
    private fun fixture() = JSONObject(javaClass.getResource("/skland/player-redacted.json")!!.readText())

    @Test fun fifteenSlotsKeepMissingMetricsUnknownAndSplitServerRewards() {
        val snapshot = ArknightsParser.snapshot(fixture(), "000000001", 1700000000000)
        assertEquals(15, (realtimeLabels + buildingLabels).distinct().size)
        assertNull(dashboardMetric(snapshot, "公招刷新"))
        assertNull(dashboardMetric(snapshot, "线索收集"))
        assertNull(dashboardMetric(snapshot, "每周任务"))
        assertEquals("4/24", dashboardMetric(snapshot, "数据增补仪")!!.amount())
        assertEquals("8/60", dashboardMetric(snapshot, "数据增补条")!!.amount())
        assertEquals("12/84", dashboardMetric(snapshot, "保全酬劳")!!.amount())
        assertNotNull(dashboardMetric(snapshot, "休息进度"))
    }

    @Test fun weeklyProgressIsOptionalAndValidated() {
        val data = fixture()
        data.getJSONObject("routine").put("weekly", JSONObject().put("current", 2).put("total", 5))
        assertEquals("2/5", dashboardMetric(ArknightsParser.snapshot(data, "000000001", 1), "每周任务")!!.amount())
        data.getJSONObject("routine").getJSONObject("weekly").put("current", 8)
        assertNull(dashboardMetric(ArknightsParser.snapshot(data, "000000001", 1), "每周任务"))
    }

    @Test fun completeRecruitmentHasDetailWithoutTimerAndMissingMaximumIsNotComplete() {
        val snapshot = ArknightsParser.snapshot(fixture(), "000000001", 1700000000000)
        fun recruitment(current: Int, total: Int?) = snapshot.copy(metrics = snapshot.metrics.filter {
            canonicalMetricLabel(it.label) != "公开招募"
        } + WidgetMetric("公开招募", current, total))
        assertEquals("招募已全部完成", dashboardDetail(recruitment(4, 4), "公开招募", snapshot.fetchedAt))
        assertNull(dashboardDetail(recruitment(3, 4), "公开招募", snapshot.fetchedAt))
        assertNull(dashboardDetail(recruitment(0, null), "公开招募", snapshot.fetchedAt))
    }

    @Test fun weeklyJadeFollowsServerProgressAndOldDashboardRequiresRefresh() {
        val data = fixture()
        val reward = data.getJSONObject("campaign").getJSONObject("reward")
        val now = 1700000000000L
        reward.put("current", 1800).put("total", 1800)
        val full = ArknightsParser.snapshot(data, "000000001", now)
        assertEquals("1800/1800", dashboardMetric(full, "每周报酬合成玉")!!.amount())
        reward.put("current", 0)
        val reset = ArknightsParser.snapshot(data, "000000001", now + 300000)
        assertEquals("0/1800", dashboardMetric(reset, "每周报酬合成玉")!!.amount())
        reward.put("current", 400)
        assertEquals("400/1800", dashboardMetric(ArknightsParser.snapshot(data, "000000001", now), "每周报酬合成玉")!!.amount())
        assertFalse(dashboardNeedsRefresh(full, now + 299999))
        assertTrue(dashboardNeedsRefresh(full, now + 300000))
        assertFalse(dashboardNeedsRefresh(reset, now + 300000))
        assertTrue(dashboardNeedsRefresh(null, now))
    }

    @Test fun officialProgressUsesStoreTimeRatherThanTheFreshHttpTimestamp() {
        fun timestamp(value: String) = java.time.OffsetDateTime.parse(value).toEpochSecond()
        val data = fixture()
        val monday = timestamp("2026-10-12T11:16:00+08:00")
        data.put("currentTs", monday)
        data.getJSONObject("status").put("storeTs", timestamp("2026-10-11T22:00:00+08:00"))
            .put("lastOnlineTs", monday)
        val routine = data.getJSONObject("routine")
        routine.put("daily", JSONObject().put("current", 10).put("total", 10))
            .put("weekly", JSONObject().put("current", 13).put("total", 13))
        val reset = ArknightsParser.snapshot(data, "000000001", monday * 1000)
        assertEquals("0/10", dashboardMetric(reset, "每日任务")!!.amount())
        assertEquals("0/13", dashboardMetric(reset, "每周任务")!!.amount())
        assertEquals("0/1800", dashboardMetric(reset, "每周报酬合成玉")!!.amount())
        assertEquals("4/24", dashboardMetric(reset, "数据增补仪")!!.amount())
        assertEquals("3天12小时后刷新", dashboardDetail(reset, "数据增补仪", reset.fetchedAt))
        data.getJSONObject("status").put("storeTs", timestamp("2026-10-12T04:00:00+08:00"))
        val fresh = ArknightsParser.snapshot(data, "000000001", monday * 1000)
        assertEquals("10/10", dashboardMetric(fresh, "每日任务")!!.amount())
        assertEquals("13/13", dashboardMetric(fresh, "每周任务")!!.amount())
        assertEquals("1800/1800", dashboardMetric(fresh, "每周报酬合成玉")!!.amount())
        data.put("currentTs", timestamp("2026-10-16T00:00:00+08:00"))
        val towerReset = ArknightsParser.snapshot(data, "000000001", monday * 1000)
        assertEquals("0/24", dashboardMetric(towerReset, "数据增补仪")!!.amount())
        assertEquals("0/60", dashboardMetric(towerReset, "数据增补条")!!.amount())
        assertEquals("0/84", dashboardMetric(towerReset, "保全酬劳")!!.amount())
        data.getJSONObject("routine").getJSONObject("daily").put("current", 11)
        assertNull(dashboardMetric(ArknightsParser.snapshot(data, "000000001", monday * 1000), "每日任务"))
    }

    @Test fun resetBoundariesHandleShanghaiDailyWeeklyAndMonthlyRollover() {
        fun millis(value: String) = java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
        for ((label, from, to) in listOf(
            Triple("每日任务", "2026-10-12T03:59:59+08:00", "2026-10-12T04:00:00+08:00"),
            Triple("每日任务", "2026-10-12T04:00:00+08:00", "2026-10-13T04:00:00+08:00"),
            Triple("每周报酬合成玉", "2026-10-12T03:59:59+08:00", "2026-10-12T04:00:00+08:00"),
            Triple("每周任务", "2026-10-12T04:00:00+08:00", "2026-10-19T04:00:00+08:00"),
            Triple("数据增补仪", "2026-10-16T00:00:00+08:00", "2026-11-16T00:00:00+08:00"),
            Triple("数据增补条", "2028-02-29T10:00:00+08:00", "2028-03-16T00:00:00+08:00"))) {
            assertEquals(millis(to), nextDashboardReset(label, millis(from)))
        }
    }

    @Test fun hireRefreshAndMeetingCluesAreParsedWithoutInventingMissingOrElapsedCounts() {
        val data = fixture()
        val building = data.getJSONObject("building")
        val hire = JSONObject().put("refreshCount", 2).put("completeWorkTime", 1700000300)
        val clue = JSONObject().put("own", 3).put("needReceive", 7).put("received", 30)
        val meeting = JSONObject().put("clue", clue).put("completeWorkTime", 1699999900)
        building.put("hire", hire).put("meeting", meeting)
        data.getJSONObject("routine").put("weekly", JSONObject().put("current", 2).put("total", 5))
        val now = 1700000000000L
        val snapshot = ArknightsParser.snapshot(data, "000000001", now)
        assertEquals(5, snapshot.dashboardMetrics.size)
        val refresh = dashboardMetric(snapshot, "公招刷新")!!
        assertEquals("2/3", refresh.amount())
        assertEquals(now + 300000, refresh.recoveryAt)
        assertEquals(MetricTimer.NEXT_COMPLETION, refresh.timer)
        val clues = dashboardMetric(snapshot, "线索收集")!!
        assertEquals("7/7", clues.amount())
        assertEquals(now, clues.recoveryAt)
        assertEquals(snapshot, SnapshotJson.read(SnapshotJson.write(snapshot)))
        hire.put("refreshCount", 3).remove("completeWorkTime")
        meeting.remove("completeWorkTime")
        val noTimer = ArknightsParser.snapshot(data, "000000001", now)
        assertEquals("3/3", dashboardMetric(noTimer, "公招刷新")!!.amount())
        assertNull(dashboardMetric(noTimer, "公招刷新")!!.recoveryAt)
        assertEquals("7/7", dashboardMetric(noTimer, "线索收集")!!.amount())
        hire.put("refreshCount", 4)
        meeting.getJSONObject("clue").put("needReceive", -1)
        val invalid = ArknightsParser.snapshot(data, "000000001", now)
        assertNull(dashboardMetric(invalid, "公招刷新"))
        assertNull(dashboardMetric(invalid, "线索收集"))
        assertTrue(invalid.metrics.isNotEmpty())
    }

    @Test fun screenshotWordingUsesAvailabilityAndShanghaiResetTimes() {
        val data = fixture()
        data.getJSONObject("building").put("hire", JSONObject().put("refreshCount", 1))
        val snapshot = ArknightsParser.snapshot(data, "000000001", 1700000000000)
        assertEquals("可进行公开招募标签刷新", dashboardDetail(snapshot, "公招刷新", snapshot.fetchedAt))
        assertTrue(dashboardDetail(snapshot, "每日任务", snapshot.fetchedAt)!!.endsWith("后刷新"))
        assertEquals("9小时29分钟", durationWords((9 * 60 + 29) * 60000L))
        assertEquals("3天22小时", durationWords((3 * 24 + 22) * 3600000L))
        for (training in listOf(WidgetTraining(TrainingState.COMPLETE),
            WidgetTraining(TrainingState.TRAINING, endsAt = snapshot.fetchedAt))) {
            assertEquals("干员已完成专精", dashboardDetail(snapshot.copy(training = training), "训练室", snapshot.fetchedAt))
        }
    }

    @Test fun roleCacheRoundTripFailureIsolationAndClear() {
        val folder = Files.createTempDirectory("tsuki-role-test").toFile()
        try {
            val store = RoleDataStore(folder, "skland")
            val snapshot = ArknightsParser.snapshot(fixture(), "000000001", 1700000000000)
            val original = RoleData(snapshot, RefreshFailure.NETWORK, 123)
            store.write("skland", "account-a", "000000001", original)
            store.write("skland", "account-b", "000000001", RoleData(snapshot))
            assertEquals(original, store.read("skland", "account-a", "000000001"))
            store.clear("account-a")
            assertNull(store.read("skland", "account-a", "000000001").snapshot)
            assertEquals(snapshot, store.read("skland", "account-b", "000000001").snapshot)
        } finally { folder.deleteRecursively() }
    }

    @Test fun accountUpdatesRejectWrongIdentityAndDuplicateOwnership() {
        validateAccountUpdate(listOf("1", "2"), listOf("2", "3"), listOf("4"))
        for (roles in listOf(listOf("9"), listOf("2", "4"), emptyList())) {
            assertThrows(IllegalArgumentException::class.java) {
                validateAccountUpdate(listOf("1", "2"), roles, listOf("4"))
            }
        }
    }

    @Test fun roleCacheRejectsTraversalAndUnknownSchema() {
        val folder = Files.createTempDirectory("tsuki-role-test").toFile()
        try {
            val store = RoleDataStore(folder, "skland")
            assertThrows(IllegalArgumentException::class.java) { store.read("skland", "../outside", "1") }
            assertThrows(IllegalArgumentException::class.java) { store.read("../skland", "account", "1") }
            assertThrows(IllegalArgumentException::class.java) { store.read("skland.endfield", "account", "../1") }
            java.io.File(folder, "account.1.json").writeText("{\"version\":99}")
            assertThrows(IllegalArgumentException::class.java) { store.read("skland", "account", "1") }
        } finally { folder.deleteRecursively() }
    }
}
