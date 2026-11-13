package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.providers.EndfieldParser
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import java.io.File
import java.util.Properties
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Metric identity: explicit IDs, the label fallback of Arknights data sources, and IDs in stored widget records.
class MetricIdsTest {
    @get:Rule val directory = TemporaryFolder()
    private val file get() = File(directory.root, "10.properties")
    private fun repository() = FileWidgetRepository(directory.root)
    private fun stored() = Properties().apply { file.inputStream().use { load(it) } }
    // Callers pick the label fallback by the record's data source.
    private fun WidgetRecord.stableIds() = snapshot!!.metrics.map { it.stableId(config.providerId in ARKNIGHTS_SOURCES) }

    @Test fun snapshotsWithoutIdsResolveArknightsMetricsByLabel() {
        // A metric as 2.1.1 stored it: no "id" key, and an old short label.
        val legacy = JSONArray().put(JSONObject().put("label", "休息").put("value", 18).put("maximum", 20)
            .put("timer", "FULL").put("estimated", false))
        val metric = SnapshotJson.readMetrics(legacy, 12).single()
        assertNull(metric.metricId)
        assertEquals("arknights.dormitory_rest", metric.stableId(arknightsSource = true))
        assertEquals("arknights.sanity", WidgetMetric("理智", 1, 135).stableId(arknightsSource = true))
        assertNull(WidgetMetric("未知指标", 1).stableId(arknightsSource = true))
        assertNull(metric.stableId(arknightsSource = false))
        assertNull(WidgetMetric("理智", 1, 135).stableId(arknightsSource = false))
    }

    @Test fun explicitIdsRoundTripAndWinOverTheLabel() {
        val metric = WidgetMetric("理智", 125, 125, metricId = "endfield.sanity")
        val read = SnapshotJson.readMetrics(SnapshotJson.metrics(listOf(metric)), 12).single()
        assertEquals(metric, read)
        assertEquals("endfield.sanity", read.stableId(arknightsSource = false))
        assertEquals("endfield.sanity", read.stableId(arknightsSource = true))
        assertFalse(SnapshotJson.metrics(listOf(WidgetMetric("理智", 1))).getJSONObject(0).has("id"))
    }

    @Test fun malformedIdsAreRejected() {
        for (bad in listOf("", "sanity", "Endfield.sanity", "endfield.", "a.b.c.d", "endfield.理智")) {
            assertThrows(IllegalArgumentException::class.java) { WidgetMetric("理智", 1, metricId = bad) }
        }
    }

    @Test fun widgetRecordsKeepEndfieldMetricIdsAcrossAPropertiesRoundTrip() {
        val card = JSONObject(requireNotNull(javaClass.getResource("/skland/endfield-card-redacted.json")).readText())
        val snapshot = EndfieldParser.snapshot(card, "0000000011", 1_793_956_830_000L)
        val ids = listOf("endfield.sanity", "endfield.daily_activation", "endfield.weekly_mission", "endfield.pass_level",
            "endfield.seek_suspicion")
        assertEquals(ids, snapshot.metrics.map { it.metricId })
        val config = WidgetInstanceConfig(10, ENDFIELD, "skland.endfield.status", "account-a", "1~0000000011")
        repository().write(WidgetRecord(config, snapshot, RefreshState.READY))
        val reopened = repository().read(10)!!
        assertEquals(snapshot, reopened.snapshot)
        assertEquals(ids, reopened.snapshot!!.metrics.map { it.metricId })
        assertEquals(ids, reopened.stableIds())
        // The ID is an added optional key; the configuration version is unchanged.
        assertEquals("4", stored().getProperty("version"))
        assertEquals("endfield.sanity", stored().getProperty("metric.0.id"))
    }

    @Test fun endfieldRecordsWrittenWithoutIdsNeverResolveToArknightsMetrics() {
        val labels = listOf("理智", "活跃度", "每周事务", "通行证", "蚀像寻遗")
        writeAsInstalled(ENDFIELD, "skland.endfield.status", "1~0000000011", labels)
        val record = repository().read(10)!!
        assertEquals(labels, record.snapshot!!.metrics.map { it.label })
        assertEquals(RefreshState.READY, record.state)
        // Unidentified until the next refresh stores real IDs; in particular "理智" is not arknights.sanity.
        assertEquals(List(5) { null }, record.snapshot.metrics.map { it.metricId })
        assertEquals(List(5) { null }, record.stableIds())
        // Saving the record again (a theme change, a failed refresh) does not invent IDs.
        repository().write(record)
        assertEquals(record, repository().read(10))
        assertTrue(stored().stringPropertyNames().none { it.endsWith(".id") })
    }

    @Test fun arknightsRecordsWrittenWithoutIdsStillResolveByLabel() {
        for (source in ARKNIGHTS_SOURCES) {
            writeAsInstalled(source, "skland.arknights.status", "000000001", listOf("理智", "休息", "未知指标"))
            val record = repository().read(10)!!
            assertEquals(List(3) { null }, record.snapshot!!.metrics.map { it.metricId })
            assertEquals(listOf("arknights.sanity", "arknights.dormitory_rest", null), record.stableIds())
        }
    }

    // A version 4 widget record in the format 2.2.0 and 2.2.1 wrote: the same keys, and no "metric.N.id".
    private fun writeAsInstalled(providerId: String, featureId: String, characterId: String, labels: List<String>) {
        val metrics = labels.withIndex().joinToString("\n") { (index, label) ->
            listOf("label=$label", "value=1", "maximum=100", "timer=FULL", "estimated=false")
                .joinToString("\n") { "metric.$index.$it" }
        }
        val text = """
            version=4
            providerId=$providerId
            featureId=$featureId
            accountId=account-a
            characterId=$characterId
            theme=DARK
            simulateFailure=false
            state=READY
            retryAt=0
            snapshot.title=测试角色
            snapshot.dashboard=[]
            snapshot.fetchedAt=1793956830000
            snapshot.dataAt=1793956828000
            snapshot.dataTimeKnown=true
            training.state=UNKNOWN
            snapshot.notes=0
            snapshot.count=${labels.size}
        """.trimIndent() + "\n" + metrics
        // Stored through Properties so that the file has the escaping the app's own writer produces.
        val values = Properties().apply { load(text.reader()) }
        file.outputStream().use { values.store(it, null) }
    }

    private companion object {
        const val ENDFIELD = "skland.endfield"
        val ARKNIGHTS_SOURCES = setOf(SklandArknightsAdapter.ID, FakeArknightsAdapter.ID)
    }
}
