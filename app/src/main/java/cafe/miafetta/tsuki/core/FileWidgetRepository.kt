package cafe.miafetta.tsuki.core

import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Properties

class FileWidgetRepository(private val directory: File) : WidgetInstanceRepository {
    private fun file(id: Int): File {
        require(id > 0)
        return File(directory, "$id.properties")
    }

    @Synchronized override fun read(id: Int): WidgetRecord? {
        val file = file(id)
        if (!file.exists()) return null
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        fun required(key: String) = requireNotNull(properties.getProperty(key)) { "Missing widget field" }
        require(properties.getProperty("version", "0").toInt() in 0..4) { "Unsupported configuration version" }
        val config = WidgetInstanceConfig(id,
            required("providerId"), required("featureId"),
            required("accountId"), required("characterId"),
            WidgetTheme.valueOf(properties.getProperty("theme", "DARK")),
            properties.getProperty("simulateFailure", "false").toBooleanStrict(),
        )
        val snapshot = properties.getProperty("snapshot.title")?.let { title ->
            val count = required("snapshot.count").toInt()
            require(count in 1..12)
            WidgetSnapshot(title, (0 until count).map { index ->
                WidgetMetric(required("metric.$index.label"),
                    required("metric.$index.value").toInt(),
                    properties.getProperty("metric.$index.maximum")?.toInt(),
                    properties.getProperty("metric.$index.recoveryAt")?.toLong(),
                    MetricTimer.valueOf(properties.getProperty("metric.$index.timer", "FULL")),
                    properties.getProperty("metric.$index.text"),
                    properties.getProperty("metric.$index.estimated", "false").toBooleanStrict(),
                    properties.getProperty("metric.$index.id"))
            }, required("snapshot.fetchedAt").toLong(),
                (0 until properties.getProperty("snapshot.notes", "0").toInt().also { require(it in 0..12) })
                    .map { required("note.$it") },
                properties.getProperty("snapshot.dataAt", required("snapshot.fetchedAt")).toLong(),
                properties.getProperty("snapshot.avatarUrl"),
                WidgetTraining(TrainingState.valueOf(properties.getProperty("training.state", "UNKNOWN")),
                    properties.getProperty("training.traineeId"), properties.getProperty("training.name"),
                    properties.getProperty("training.skill")?.toInt(), properties.getProperty("training.endsAt")?.toLong(),
                    properties.getProperty("training.avatarUrl"), properties.getProperty("training.remainingSecs")?.toLong()),
                properties.containsKey("snapshot.dataAt") && properties.getProperty("snapshot.dataTimeKnown", "true").toBooleanStrict(),
                SnapshotJson.readMetrics(org.json.JSONArray(properties.getProperty("snapshot.dashboard", "[]")), 5))
        }
        return WidgetRecord(config, snapshot, RefreshState.valueOf(properties.getProperty("state",
            if (snapshot == null) "LOADING" else "READY")),
            properties.getProperty("failure")?.let(RefreshFailure::valueOf),
            properties.getProperty("retryAt", "0").toLong())
    }

    @Synchronized override fun write(record: WidgetRecord) {
        val properties = Properties().apply {
            setProperty("version", "4")
            setProperty("providerId", record.config.providerId)
            setProperty("featureId", record.config.featureId)
            setProperty("accountId", record.config.accountId)
            setProperty("characterId", record.config.characterId)
            setProperty("theme", record.config.theme.name)
            setProperty("simulateFailure", record.config.simulateFailure.toString())
            setProperty("state", record.state.name)
            record.failure?.let { setProperty("failure", it.name) }
            setProperty("retryAt", record.retryAt.toString())
            record.snapshot?.let { snapshot ->
                setProperty("snapshot.title", snapshot.title)
                setProperty("snapshot.dashboard", SnapshotJson.metrics(snapshot.dashboardMetrics).toString())
                setProperty("snapshot.fetchedAt", snapshot.fetchedAt.toString())
                setProperty("snapshot.dataAt", snapshot.dataAt.toString())
                setProperty("snapshot.dataTimeKnown", snapshot.dataTimeKnown.toString())
                snapshot.avatarUrl?.let { setProperty("snapshot.avatarUrl", it) }
                snapshot.training.let { training ->
                    setProperty("training.state", training.state.name)
                    training.traineeId?.let { setProperty("training.traineeId", it) }
                    training.name?.let { setProperty("training.name", it) }
                    training.skill?.let { setProperty("training.skill", it.toString()) }
                    training.endsAt?.let { setProperty("training.endsAt", it.toString()) }
                    training.remainingSecs?.let { setProperty("training.remainingSecs", it.toString()) }
                    training.avatarUrl?.let { setProperty("training.avatarUrl", it) }
                }
                setProperty("snapshot.notes", snapshot.notes.size.toString())
                snapshot.notes.forEachIndexed { index, note -> setProperty("note.$index", note) }
                setProperty("snapshot.count", snapshot.metrics.size.toString())
                snapshot.metrics.forEachIndexed { index, metric ->
                    setProperty("metric.$index.label", metric.label)
                    setProperty("metric.$index.value", metric.value.toString())
                    metric.maximum?.let { setProperty("metric.$index.maximum", it.toString()) }
                    metric.recoveryAt?.let { setProperty("metric.$index.recoveryAt", it.toString()) }
                    setProperty("metric.$index.timer", metric.timer.name)
                    metric.text?.let { setProperty("metric.$index.text", it) }
                    setProperty("metric.$index.estimated", metric.estimated.toString())
                    // Optional key added in 2.2.2; records written before it have no metric IDs (see MetricIds.kt).
                    metric.metricId?.let { setProperty("metric.$index.id", it) }
                }
            }
        }
        val bytes = ByteArrayOutputStream().apply { properties.store(this, null) }.toByteArray()
        writeAtomic(file(record.config.widgetId), bytes)
    }

    // Every widget ID with stored data, including instances the launcher no longer lists.
    @Synchronized fun ids(): List<Int> = directory.listFiles()
        ?.mapNotNull { it.name.removeSuffix(".properties").toIntOrNull() }?.filter { it > 0 } ?: emptyList()

    @Synchronized override fun delete(id: Int) {
        Files.deleteIfExists(file(id).toPath())
    }

    @Synchronized override fun remap(oldIds: IntArray, newIds: IntArray) {
        require(oldIds.size == newIds.size && oldIds.distinct().size == oldIds.size &&
            newIds.distinct().size == newIds.size && (oldIds + newIds).all { it > 0 })
        // shortcut: restore needs fresh destination IDs; add a transaction journal if a Launcher reuses live IDs.
        require(oldIds.indices.all { newIds[it] == oldIds[it] || newIds[it] !in oldIds })
        val records = oldIds.map { read(it) }
        newIds.forEachIndexed { index, id ->
            val destination = read(id)
            val expected = records[index]?.let { it.copy(config = it.config.copy(widgetId = id)) }
            require(expected == null || id in oldIds || destination == null || destination == expected) {
                "Destination already has widget data"
            }
        }
        records.forEachIndexed { index, record ->
            if (record != null) write(record.copy(config = record.config.copy(widgetId = newIds[index])))
        }
        oldIds.filter { it !in newIds }.forEach { delete(it) }
    }
}
