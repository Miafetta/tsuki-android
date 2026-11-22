package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceFailure
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetSnapshot
import org.json.JSONObject

// The Endfield card (/web/v1/game/endfield/card/detail) read into the metrics of the official realtime-data page; field
// mapping follows the page's modules. Labels are the page's names; metric IDs are the stable identity.
internal object EndfieldParser {
    const val SANITY = "endfield.sanity"

    // Realtime modules in the page's default order: module ID, label, metric ID, object, current key, maximum key.
    private val modules = listOf(
        Module("REALTIME_MODULE_DAILY_MISSION", "活跃度", "endfield.daily_activation", "dailyMission", "dailyActivation",
            "maxDailyActivation"),
        Module("REALTIME_MODULE_WEEKLY_MISSION", "每周事务", "endfield.weekly_mission", "weeklyMission", "score", "total"),
        Module("REALTIME_MODULE_BP_SYSTEM", "通行证", "endfield.pass_level", "bpSystem", "curLevel", "maxLevel"),
        Module("REALTIME_MODULE_SEEK_SUSPICION", "蚀像寻遗", "endfield.seek_suspicion", "seekSuspicion", "count", "total",
            clamp = true),
    )

    // Sanity is required; a missing or malformed realtime module only adds a note.
    fun snapshot(data: JSONObject, roleId: String, receivedAt: Long): WidgetSnapshot = try {
        read(data.getJSONObject("detail"), roleId, receivedAt)
    } catch (_: org.json.JSONException) { throw DataSourceFailure(RefreshFailure.INVALID_DATA) }
    catch (_: IllegalArgumentException) { throw DataSourceFailure(RefreshFailure.INVALID_DATA) }

    private fun read(detail: JSONObject, roleId: String, receivedAt: Long): WidgetSnapshot {
        val base = detail.getJSONObject("base")
        require(base.getString("roleId") == roleId)
        val serverNow = detail.getString("currentTs").toLong().also { require(it in 1..32_503_680_000L) }
        val notes = mutableListOf<String>()
        val metrics = mutableListOf(sanity(detail.getJSONObject("dungeon"), serverNow, receivedAt, notes))
        for (module in order(detail)) {
            try { metrics += module.read(detail) }
            catch (_: org.json.JSONException) { notes += "${module.label}数据缺失" }
            catch (_: IllegalArgumentException) { notes += "${module.label}数据缺失" }
        }
        return WidgetSnapshot(base.getString("name").take(80).ifBlank { "终末地角色" }, metrics, receivedAt, notes,
            serverNow * 1000)
    }

    // maxTs is the full-recovery time in server seconds and "0" once sanity is full. The recovery rate is not in the
    // response, so the value between syncs is shown as reported rather than estimated.
    private fun sanity(dungeon: JSONObject, serverNow: Long, receivedAt: Long, notes: MutableList<String>): WidgetMetric {
        val maximum = dungeon.getString("maxStamina").toInt().also { require(it in 1..10000) }
        val current = dungeon.getString("curStamina").toInt().also { require(it in 0..100000) }
        val fullAt = dungeon.optString("maxTs", "0").toLong().also { require(it in 0..32_503_680_000L) }
        val recoveryAt = when {
            current >= maximum -> 0
            fullAt > 0 -> receivedAt + (fullAt - serverNow).coerceAtLeast(0) * 1000
            else -> null
        }
        if (recoveryAt == null) notes += "理智恢复时间未知"
        return WidgetMetric("理智", current, maximum, recoveryAt, metricId = SANITY)
    }

    // The page order the user set in Skland; modules it does not list follow in the default order.
    private fun order(detail: JSONObject): List<Module> {
        val listed = detail.optJSONObject("config")?.optJSONArray("realtimeModules")
        val ids = (0 until (listed?.length() ?: 0)).mapNotNull { listed?.optJSONObject(it)?.optString("id") }
        val chosen = ids.mapNotNull { id -> modules.firstOrNull { it.id == id } }.distinct()
        return chosen + modules.filter { it !in chosen }
    }

    private data class Module(val id: String, val label: String, val metricId: String, val key: String,
        val currentKey: String, val maximumKey: String, val clamp: Boolean = false) {
        fun read(detail: JSONObject): WidgetMetric {
            val value = detail.getJSONObject(key)
            val maximum = value.getInt(maximumKey).also { require(it in 1..10_000_000) }
            val current = value.getInt(currentKey).also { require(it in 0..10_000_000) }
            return WidgetMetric(label, if (clamp) current.coerceAtMost(maximum) else current, maximum, metricId = metricId)
        }
    }
}
