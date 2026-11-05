package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.core.TrainingState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// Player response parsing: progress metrics, training, avatars and empty or malformed buildings.
class ArknightsProgressTest : SklandFixtureTest() {
    @Test fun newProgressMetricsIncludeBothTowerResourcesAndSyncRestCounts() {
        val data = fixture("production")
        val snapshot = ArknightsParser.snapshot(data, "000000001", now)
        fun metric(label: String) = snapshot.metrics.single { it.label == label }
        assertEquals("每日任务 7/10", metric("每日任务").display())
        assertEquals("合成玉 1800/1800", metric("合成玉").display())
        assertEquals("训练室 空闲中", metric("训练室").display())
        assertEquals("宿舍休息 18/20", metric("休息").display())
        assertEquals("保全酬劳 0/84", metric("保全酬劳").display())
        val reward = data.getJSONObject("tower").getJSONObject("reward")
        reward.getJSONObject("higherItem").put("current", 24)
        reward.getJSONObject("lowerItem").put("current", 60)
        assertEquals("保全酬劳 84/84", ArknightsParser.snapshot(data, "000000001", now).metrics.last().display())
    }

    @Test fun trainingHandlesActiveCompletedPausedAndIdleSlots() {
        for (mode in listOf("active", "completed", "paused", "idle")) {
            val data = fixture("player")
            val training = data.getJSONObject("building").getJSONObject("training")
            training.put("trainee", JSONObject().put("charId", "char_test_trainee").put("targetSkill", if (mode == "idle") -1 else 2))
                .put("remainSecs", if (mode == "completed") 0 else 3600)
                .put("lastUpdateTime", now / 1000 - 600)
                .put("speed", if (mode == "paused") 0 else 1.5)
            val metric = ArknightsParser.snapshot(data, "000000001", now).metrics.single { it.label == "训练室" }
            when (mode) {
                "active" -> {
                    assertEquals("训练室 名称未知，3技能，训练中", metric.display())
                    assertEquals(now + 3600000, metric.recoveryAt)
                    assertEquals("训练 1时0分后完成", ArknightsParser.countdown(metric, now))
                }
                "completed" -> {
                    assertEquals("训练室 名称未知，3技能，已完成", metric.display())
                    assertEquals(1, metric.value)
                    assertEquals("训练预计已完成", ArknightsParser.countdown(metric, now))
                }
                "paused" -> {
                    assertEquals("训练室 名称未知，3技能，暂停", metric.display())
                    assertNull(metric.recoveryAt)
                    val info = ArknightsParser.snapshot(data, "000000001", now).training
                    assertEquals("1时0分（同步）", info.time(now))
                    assertEquals(info.time(now), info.time(now + 600000))
                }
                else -> {
                    assertEquals("训练室 空闲中", metric.display())
                    assertNull(metric.recoveryAt)
                }
            }
        }
    }

    @Test fun trainingUsesTraineeIdentityAndSecondsWithoutDividingBySpeed() {
        val data = fixture("player")
        data.put("charInfoMap", JSONObject().put("char_test_trainee", JSONObject().put("name", "测试受训干员"))
            .put("char_test_trainer", JSONObject().put("name", "测试协助干员")))
        val training = data.getJSONObject("building").getJSONObject("training")
        training.put("trainee", JSONObject().put("charId", "char_test_trainee").put("targetSkill", 1))
            .put("trainer", JSONObject().put("charId", "char_test_trainer"))
            .put("lastUpdateTime", now / 1000).put("remainSecs", 3600).put("speed", 2.5)
        val parsed = ArknightsParser.snapshot(data, "000000001", now).training
        assertEquals("char_test_trainee", parsed.traineeId)
        assertEquals("测试受训干员", parsed.name)
        assertTrue(parsed.avatarUrl!!.endsWith("/char_test_trainee.png"))
        assertFalse(parsed.avatarUrl.contains("trainer"))
        assertEquals(now + 3600000, parsed.endsAt)
        assertEquals("1时0分", parsed.time(now))
        training.put("lastUpdateTime", now / 1000 - 1800)
        assertEquals(now + 3600000, ArknightsParser.snapshot(data, "000000001", now).training.endsAt)
        training.put("remainSecs", 0)
        assertEquals(TrainingState.COMPLETE, ArknightsParser.snapshot(data, "000000001", now).training.state)
        training.put("remainSecs", -1)
        assertEquals(TrainingState.UNKNOWN, ArknightsParser.snapshot(data, "000000001", now).training.state)
        training.remove("remainSecs")
        val missing = ArknightsParser.snapshot(data, "000000001", now)
        assertEquals(TrainingState.UNKNOWN, missing.training.state)
        assertEquals("char_test_trainee", missing.training.traineeId)
        assertTrue(missing.training.avatarUrl!!.endsWith("/char_test_trainee.png"))
        assertTrue(missing.notes.contains("训练室 未提供"))
        training.put("trainee", JSONObject().put("charId", "char_test_trainee"))
        assertEquals(TrainingState.UNKNOWN, ArknightsParser.snapshot(data, "000000001", now).training.state)
        training.getJSONObject("trainee").put("targetSkill", -1)
        val occupied = ArknightsParser.snapshot(data, "000000001", now).training
        assertEquals(TrainingState.IDLE, occupied.state)
        assertEquals("char_test_trainee", occupied.traineeId)
        assertNotNull(occupied.avatarUrl)
        training.put("trainee", JSONObject.NULL)
        assertNull(ArknightsParser.snapshot(data, "000000001", now).training.traineeId)
    }

    @Test fun avatarUrlsUseOfficialResourcesAndMissingSourcesRemainPlaceholders() {
        val data = fixture("player")
        val status = data.getJSONObject("status")
        val official = "https://web.hycdn.cn/arknights/game/assets/avatar/test.png"
        status.put("avatar", JSONObject().put("url", official))
        assertEquals(official, ArknightsParser.snapshot(data, "000000001", now).avatarUrl)
        status.getJSONObject("avatar").put("url", "https://third-party.test/avatar.png")
        assertNull(ArknightsParser.snapshot(data, "000000001", now).avatarUrl)
        status.remove("avatar")
        assertNull(ArknightsParser.snapshot(data, "000000001", now).avatarUrl)
    }

    @Test fun validEmptyBuildingsKeepZeroCapacitiesButMissingCollectionsAreUnknown() {
        val data = fixture("player")
        data.put("recruit", org.json.JSONArray())
        val building = data.getJSONObject("building")
        building.put("tradings", org.json.JSONArray()).put("manufactures", org.json.JSONArray().put(
            JSONObject().put("formulaId", "").put("complete", 0)))
        val snapshot = ArknightsParser.snapshot(data, "000000001", now)
        for (label in listOf("公开招募", "订单进度", "制造进度"))
            assertEquals("$label 0/0", snapshot.metrics.single { it.label == label }.display())
        building.getJSONArray("manufactures").getJSONObject(0).put("complete", 5)
        val withoutFormula = ArknightsParser.snapshot(data, "000000001", now)
        assertEquals("制造进度 5/未知", withoutFormula.metrics.single { it.label == "制造进度" }.display())
        assertTrue(withoutFormula.notes.any { it.contains("容量未知") })
        building.remove("manufactures")
        val partial = ArknightsParser.snapshot(data, "000000001", now)
        assertFalse(partial.metrics.any { it.label == "制造进度" })
        assertTrue(partial.notes.contains("制造进度 未提供"))
    }

    @Test fun towerProgressSumsBothServerRewardTypesAndDoesNotUseScreenshotConstants() {
        val data = fixture("player")
        val reward = data.getJSONObject("tower").getJSONObject("reward")
        reward.getJSONObject("higherItem").put("current", 2).put("total", 11)
        reward.getJSONObject("lowerItem").put("current", 3).put("total", 17)
        val metric = ArknightsParser.snapshot(data, "000000001", now).metrics.single { it.label == "保全酬劳" }
        assertEquals("保全酬劳 5/28", metric.display())
        reward.getJSONObject("lowerItem").remove("total")
        val partial = ArknightsParser.snapshot(data, "000000001", now)
        assertFalse(partial.metrics.any { it.label == "保全酬劳" })
        assertTrue(partial.notes.contains("保全酬劳 未提供"))
    }

    @Test fun newFieldsAreIndependentAndMalformedProgressCannotAppearAsZero() {
        val missing = fixture("player")
        missing.remove("routine")
        missing.remove("campaign")
        missing.remove("tower")
        missing.getJSONObject("building").remove("training")
        missing.getJSONObject("building").remove("dormitories")
        val partial = ArknightsParser.snapshot(missing, "000000001", now)
        assertEquals(6, partial.metrics.size)
        assertEquals(5, partial.notes.size)
        for (mode in listOf("daily", "annihilation", "tower", "training", "dorm")) {
            val data = fixture("player")
            val label = when (mode) {
                "daily" -> {
                    data.getJSONObject("routine").getJSONObject("daily").put("current", -1)
                    "每日任务"
                }
                "annihilation" -> {
                    data.getJSONObject("campaign").getJSONObject("reward").put("total", 1)
                    "合成玉"
                }
                "tower" -> {
                    data.getJSONObject("tower").getJSONObject("reward").remove("lowerItem")
                    "保全酬劳"
                }
                "training" -> {
                    data.getJSONObject("building").getJSONObject("training").remove("trainee")
                    "训练室"
                }
                else -> {
                    data.getJSONObject("building").getJSONArray("dormitories").put(JSONObject().put("chars",
                        org.json.JSONArray().put(JSONObject().put("ap", -1))))
                    "休息"
                }
            }
            val snapshot = ArknightsParser.snapshot(data, "000000001", now)
            assertTrue(snapshot.notes.contains("$label 未提供"))
            assertFalse(snapshot.metrics.any { it.label == label })
            assertEquals(81, snapshot.metrics[0].value)
        }
    }

    @Test fun zeroProgressCapacityAndEmptyDormitoriesAreValidStates() {
        val data = fixture("player")
        data.getJSONObject("routine").getJSONObject("daily").put("current", 0).put("total", 0)
        data.getJSONObject("campaign").getJSONObject("reward").put("current", 0).put("total", 0)
        val reward = data.getJSONObject("tower").getJSONObject("reward")
        for (key in listOf("higherItem", "lowerItem")) reward.getJSONObject(key).put("current", 0).put("total", 0)
        val snapshot = ArknightsParser.snapshot(data, "000000001", now)
        assertTrue(snapshot.notes.isEmpty())
        for (label in listOf("每日任务", "合成玉", "保全酬劳", "休息")) {
            val metric = snapshot.metrics.single { it.label == label }
            assertEquals("${cafe.miafetta.tsuki.core.canonicalMetricLabel(label)} 0/0", metric.display())
        }
    }
}
