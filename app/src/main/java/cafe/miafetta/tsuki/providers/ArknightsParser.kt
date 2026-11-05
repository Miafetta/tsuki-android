package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.MetricTimer
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetSnapshot
import org.json.JSONObject

internal object ArknightsParser {
    // Any missing or out-of-range required field makes the whole response invalid; optional metrics only add a note.
    fun snapshot(data: JSONObject, uid: String, receivedAt: Long): WidgetSnapshot = try {
        ArknightsReader(data, uid, receivedAt).read()
    } catch (_: org.json.JSONException) { throw DataSourceFailure(RefreshFailure.INVALID_DATA) }
    catch (_: IllegalArgumentException) { throw DataSourceFailure(RefreshFailure.INVALID_DATA) }

    fun countdown(metric: WidgetMetric, now: Long): String {
        val target = metric.recoveryAt ?: return "${metric.label}恢复未知"
        val minutes = ((target - now).coerceAtLeast(0) + 59_999) / 60_000
        if (metric.label == "训练室" && metric.timer == MetricTimer.NEXT_COMPLETION) return if (minutes == 0L)
            "训练预计已完成" else "训练 ${minutes / 60}时${minutes % 60}分后完成"
        if (metric.timer == MetricTimer.NEXT_COMPLETION) return if (minutes == 0L) "${metric.label}下一项预计已完成"
            else "${metric.label}下一项 ${minutes / 60}时${minutes % 60}分后完成"
        return if (minutes == 0L) "${metric.label}已满" else
            "${metric.label} ${minutes / 60}时${minutes % 60}分后满"
    }
}
