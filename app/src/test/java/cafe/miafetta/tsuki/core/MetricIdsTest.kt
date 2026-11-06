package cafe.miafetta.tsuki.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MetricIdsTest {
    @Test fun snapshotsWithoutIdsResolveArknightsMetricsByLabel() {
        // A metric as 2.1.1 stored it: no "id" key, and an old short label.
        val legacy = JSONArray().put(JSONObject().put("label", "休息").put("value", 18).put("maximum", 20)
            .put("timer", "FULL").put("estimated", false))
        val metric = SnapshotJson.readMetrics(legacy, 12).single()
        assertNull(metric.metricId)
        assertEquals("arknights.dormitory_rest", metric.stableId())
        assertEquals("arknights.sanity", WidgetMetric("理智", 1, 135).stableId())
        assertNull(WidgetMetric("未知指标", 1).stableId())
    }

    @Test fun explicitIdsRoundTripAndWinOverTheLabel() {
        val metric = WidgetMetric("理智", 125, 125, metricId = "endfield.sanity")
        val read = SnapshotJson.readMetrics(SnapshotJson.metrics(listOf(metric)), 12).single()
        assertEquals(metric, read)
        assertEquals("endfield.sanity", read.stableId())
        assertFalse(SnapshotJson.metrics(listOf(WidgetMetric("理智", 1))).getJSONObject(0).has("id"))
    }

    @Test fun malformedIdsAreRejected() {
        for (bad in listOf("", "sanity", "Endfield.sanity", "endfield.", "a.b.c.d", "endfield.理智")) {
            assertThrows(IllegalArgumentException::class.java) { WidgetMetric("理智", 1, metricId = bad) }
        }
    }
}
