package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.core.TrainingState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// Player response parsing: primary metrics, recovery estimates, orders, manufacturing and dormitories.
class ArknightsParserTest : SklandFixtureTest() {
    @Test fun playerMappingUsesServerTimeAndParsesAllSixMetrics() {
        val snapshot = ArknightsParser.snapshot(fixture("player"), "000000001", now)
        assertEquals(listOf(81, 108, 3, 17, 1, 2, 7, 1800, 0, 0, 12), snapshot.metrics.map { it.value })
        assertEquals(listOf(135, 200, 24, 30, 4, null, 10, 1800, null, null, 84), snapshot.metrics.map { it.maximum })
        assertEquals(now + 19_440_000, snapshot.metrics[0].recoveryAt)
        assertEquals(now + 3_300_000, snapshot.metrics[1].recoveryAt)
        assertEquals(now + 600000, snapshot.metrics[2].recoveryAt)
        assertEquals(now + 900000, snapshot.metrics[3].recoveryAt)
        assertEquals(now + 300000, snapshot.metrics[4].recoveryAt)
        assertEquals("公开招募下一项 0时5分后完成", ArknightsParser.countdown(snapshot.metrics[4], now))
        assertEquals("公开招募下一项预计已完成", ArknightsParser.countdown(snapshot.metrics[4], now + 300000))
        assertEquals(now, snapshot.dataAt)
        assertTrue(snapshot.notes.isEmpty())
    }

    @Test fun overcappedSanityIsPreservedAndZeroLaborRemainingCannotDivideByZero() {
        val data = fixture("player")
        data.getJSONObject("status").getJSONObject("ap").put("current", 561).put("completeRecoveryTime", -1)
        data.getJSONObject("building").getJSONObject("labor").put("remainSecs", 0)
        val snapshot = ArknightsParser.snapshot(data, "000000001", now)
        assertEquals(561, snapshot.metrics[0].value)
        assertEquals(0L, snapshot.metrics[0].recoveryAt)
        assertEquals(200, snapshot.metrics[1].value)
    }

    @Test fun missingSecondaryFieldsAreMarkedAndWrongRoleOrMissingPrimaryFails() {
        val data = fixture("player")
        data.getJSONObject("building").remove("tradings")
        val partial = ArknightsParser.snapshot(data, "000000001", now)
        assertTrue(partial.notes.contains("订单进度 未提供"))
        assertFalse(partial.metrics.any { it.label == "订单进度" })
        reason(RefreshFailure.INVALID_DATA) { ArknightsParser.snapshot(data, "000000099", now) }
        data.getJSONObject("building").remove("labor")
        reason(RefreshFailure.INVALID_DATA) { ArknightsParser.snapshot(data, "000000001", now) }
    }

    @Test fun recoveryEstimatesHandleFullUnknownAndMinuteBoundaries() {
        assertEquals("理智已满", ArknightsParser.countdown(WidgetMetric("理智", 135, 135, now), now))
        assertEquals("理智已满", ArknightsParser.countdown(WidgetMetric("理智", 135, 135, now - 1), now))
        assertEquals("理智 0时1分后满", ArknightsParser.countdown(WidgetMetric("理智", 134, 135, now + 1), now))
        assertEquals("理智 1时0分后满", ArknightsParser.countdown(WidgetMetric("理智", 125, 135, now + 3600000), now))
        assertEquals("理智恢复未知", ArknightsParser.countdown(WidgetMetric("理智", 1, 135), now))
        val data = fixture("player")
        data.getJSONObject("building").getJSONObject("labor").put("lastUpdateTime", 1700000001)
        assertEquals(100, ArknightsParser.snapshot(data, "000000001", now).metrics[1].value)
    }

    @Test fun redactedAccountProductionMatchesTheOfficialOrderAndManufactureCounts() {
        val snapshot = ArknightsParser.snapshot(fixture("production"), "000000001", now)
        assertEquals(listOf(174, 52, 2, 10, 0, 0, 7, 1800, 0, 18, 0), snapshot.metrics.map { it.value })
        assertEquals(listOf(210, 235, 20, 64, 4, null, 10, 1800, null, 20, 84), snapshot.metrics.map { it.maximum })
        assertNull(snapshot.metrics[2].recoveryAt)
        assertTrue(snapshot.metrics[3].recoveryAt!! > now)
        assertTrue(snapshot.notes.isEmpty())
    }

    @Test fun ordersOnlyAddTheKnownCompletedOrderAndNeverExceedCapacity() {
        val data = fixture("player")
        val trading = data.getJSONObject("building").getJSONArray("tradings").getJSONObject(0)
        trading.put("completeWorkTime", now / 1000)
        var order = ArknightsParser.snapshot(data, "000000001", now).metrics[2]
        assertEquals(4, order.value)
        assertNull(order.recoveryAt)
        data.put("currentTs", now / 1000 + 86400)
        assertEquals(4, ArknightsParser.snapshot(data, "000000001", now).metrics[2].value)
        trading.put("stockLimit", 2)
        order = ArknightsParser.snapshot(data, "000000001", now).metrics[2]
        assertEquals(3, order.value)
        assertEquals(14, order.maximum)
        trading.put("stockLimit", 1)
        val invalid = ArknightsParser.snapshot(data, "000000001", now)
        assertTrue(invalid.notes.contains("订单进度 未提供"))
        assertFalse(invalid.metrics.any { it.label == "订单进度" })
    }

    @Test fun staffedTradingUsesTheOfficialThreeHourAccumulationAndStopsWhenUnstaffed() {
        val data = fixture("player")
        val station = data.getJSONObject("building").getJSONArray("tradings").getJSONObject(0)
        station.put("stock", org.json.JSONArray().put(JSONObject()).put(JSONObject()))
            .put("chars", org.json.JSONArray().put(JSONObject()))
            .put("stockLimit", 10).put("lastUpdateTime", now / 1000 - 50000)
            .put("completeWorkTime", now / 1000 - 4 * 10800)
        data.getJSONObject("building").getJSONArray("tradings").remove(1)
        val orders = ArknightsParser.snapshot(data, "000000001", now).metrics.single { it.label == "订单进度" }
        assertEquals(7, orders.value)
        assertEquals(now + 10800000, orders.recoveryAt)
        assertTrue(orders.estimated)
        station.put("completeWorkTime", now / 1000 - 20 * 10800)
        assertEquals(10, ArknightsParser.snapshot(data, "000000001", now).metrics.single { it.label == "订单进度" }.value)
        station.put("chars", org.json.JSONArray())
        val stopped = ArknightsParser.snapshot(data, "000000001", now).metrics.single { it.label == "订单进度" }
        assertEquals(2, stopped.value)
        assertNull(stopped.recoveryAt)
    }

    @Test fun dormitoryRecoveryUsesLevelComfortAndTimeWithoutInventingMissingRates() {
        val data = fixture("player")
        val chars = org.json.JSONArray((0 until 5).map { JSONObject().put("ap", if (it == 0) 8640000 else 8280000)
            .put("lastApAddTime", now / 1000 - 900) })
        val room = JSONObject().put("level", 5).put("comfort", 5000).put("chars", chars)
        data.getJSONObject("building").put("dormitories", org.json.JSONArray().put(room))
        fun rest() = ArknightsParser.snapshot(data, "000000001", now).metrics.single { it.label == "休息" }
        assertEquals(5, rest().value)
        assertTrue(rest().estimated)
        chars.getJSONObject(1).put("lastApAddTime", now / 1000 + 10)
        chars.getJSONObject(2).remove("lastApAddTime")
        assertEquals(3, rest().value)
        room.remove("comfort")
        assertEquals(1, rest().value)
        assertFalse(rest().estimated)
    }

    @Test fun manufacturingUsesFormulaCycleAndCountsExactCompletionsOnce() {
        val data = fixture("player")
        fun manufacture(seconds: Long): WidgetMetric {
            data.put("currentTs", now / 1000 + seconds)
            return ArknightsParser.snapshot(data, "000000001", now).metrics[3]
        }
        assertEquals(17, manufacture(899).value)
        assertEquals(now + 1000, manufacture(899).recoveryAt)
        assertEquals(18, manufacture(900).value)
        assertEquals(now + 300000, manufacture(900).recoveryAt)
        assertEquals(19, manufacture(1200).value)
        assertEquals(30, manufacture(8400).value)
        assertNull(manufacture(8400).recoveryAt)
        assertEquals(30, manufacture(86400).value)
    }

    @Test fun stoppedZeroSpeedAndFutureUpdateFactoriesDoNotInventProducts() {
        for (mode in listOf("stopped", "zero-speed", "empty-queue", "future-update")) {
            val data = fixture("player")
            val factories = data.getJSONObject("building").getJSONArray("manufactures")
            for (i in 0 until factories.length()) {
                val factory = factories.getJSONObject(i)
                when (mode) {
                    "stopped" -> factory.put("completeWorkTime", -1)
                    "zero-speed" -> factory.put("speed", 0)
                    "empty-queue" -> factory.put("remain", 0)
                    else -> factory.put("lastUpdateTime", now / 1000 + 3600)
                }
            }
            val metric = ArknightsParser.snapshot(data, "000000001", now).metrics[3]
            assertEquals(17, metric.value)
            assertEquals(30, metric.maximum)
            if (mode != "future-update") assertNull(metric.recoveryAt)
        }
    }

    @Test fun manufacturingStopsAtQueueEndOrOccupiedCapacity() {
        val data = fixture("player")
        val factories = data.getJSONObject("building").getJSONArray("manufactures")
        factories.getJSONObject(0).put("remain", 1).put("completeWorkTime", now / 1000 + 1200)
        factories.getJSONObject(1).put("weight", 35).put("completeWorkTime", now / 1000 + 900)
        data.put("currentTs", now / 1000 + 86400)
        val metric = ArknightsParser.snapshot(data, "000000001", now).metrics[3]
        assertEquals(18, metric.value)
        assertEquals(30, metric.maximum)
        assertNull(metric.recoveryAt)
    }

    @Test fun badManufacturingMetadataIsMarkedMissingRatherThanReportedAsZero() {
        for (mode in listOf("missing-formula", "zero-weight", "zero-cost", "negative-speed", "missing-speed", "non-finite-speed")) {
            val data = fixture("player")
            val formula = data.getJSONObject("manufactureFormulaInfoMap").getJSONObject("3")
            val factory = data.getJSONObject("building").getJSONArray("manufactures").getJSONObject(0)
            when (mode) {
                "missing-formula" -> data.remove("manufactureFormulaInfoMap")
                "zero-weight" -> formula.put("weight", 0)
                "zero-cost" -> formula.put("costPoint", 0)
                "negative-speed" -> factory.put("speed", -1)
                "non-finite-speed" -> factory.put("speed", "Infinity")
                else -> factory.remove("speed")
            }
            val snapshot = ArknightsParser.snapshot(data, "000000001", now)
            assertTrue(snapshot.notes.contains("制造进度 未提供"))
            assertFalse(snapshot.metrics.any { it.label == "制造进度" })
            assertEquals(81, snapshot.metrics[0].value)
        }
    }

}
