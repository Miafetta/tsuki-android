package cafe.miafetta.tsuki.core

import org.json.JSONArray
import org.json.JSONObject

internal object SnapshotJson {
    fun metrics(values: List<WidgetMetric>) = JSONArray(values.map { metric ->
        JSONObject().put("label", metric.label).put("value", metric.value)
            .put("maximum", metric.maximum).put("recoveryAt", metric.recoveryAt)
            .put("timer", metric.timer.name).put("text", metric.text).put("estimated", metric.estimated)
            .put("id", metric.metricId)
    })

    fun readMetrics(values: JSONArray, limit: Int): List<WidgetMetric> {
        require(values.length() <= limit)
        return (0 until values.length()).map { index ->
            val value = values.getJSONObject(index)
            WidgetMetric(value.getString("label"), value.getInt("value"),
                if (value.has("maximum")) value.getInt("maximum") else null,
                if (value.has("recoveryAt")) value.getLong("recoveryAt") else null,
                MetricTimer.valueOf(value.getString("timer")), value.optString("text").takeIf { it.isNotEmpty() },
                value.getBoolean("estimated"), value.optString("id").takeIf { it.isNotEmpty() })
        }
    }

    fun write(snapshot: WidgetSnapshot): JSONObject = JSONObject().apply {
        put("title", snapshot.title)
        put("metrics", metrics(snapshot.metrics))
        put("fetchedAt", snapshot.fetchedAt)
        put("dataAt", snapshot.dataAt)
        put("known", snapshot.dataTimeKnown)
        put("avatar", snapshot.avatarUrl)
        put("notes", JSONArray(snapshot.notes))
        put("dashboard", metrics(snapshot.dashboardMetrics))
        put("training", JSONObject().apply {
            val training = snapshot.training
            put("state", training.state.name)
            put("id", training.traineeId)
            put("name", training.name)
            put("skill", training.skill)
            put("endsAt", training.endsAt)
            put("avatar", training.avatarUrl)
            put("remaining", training.remainingSecs)
        })
    }

    fun read(value: JSONObject): WidgetSnapshot {
        val training = value.getJSONObject("training")
        fun text(key: String) = training.optString(key).takeIf { it.isNotEmpty() }
        val notes = value.getJSONArray("notes")
        require(notes.length() <= 12)
        return WidgetSnapshot(value.getString("title"), readMetrics(value.getJSONArray("metrics"), 12),
            value.getLong("fetchedAt"), (0 until notes.length()).map(notes::getString), value.getLong("dataAt"),
            value.optString("avatar").takeIf { it.isNotEmpty() },
            WidgetTraining(TrainingState.valueOf(training.getString("state")), text("id"), text("name"),
                if (training.has("skill")) training.getInt("skill") else null,
                if (training.has("endsAt")) training.getLong("endsAt") else null, text("avatar"),
                if (training.has("remaining")) training.getLong("remaining") else null), value.getBoolean("known"),
            readMetrics(value.optJSONArray("dashboard") ?: JSONArray(), 5))
    }
}
