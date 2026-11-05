package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.MetricTimer
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTraining
import org.json.JSONObject

// One player response read into a snapshot. Server timestamps are seconds; local times are receivedAt-based milliseconds.
// Building metrics live in ArknightsBuilding.kt.
internal class ArknightsReader(val data: JSONObject, uid: String, val receivedAt: Long) {
    val status: JSONObject = data.getJSONObject("status")
    val serverNow: Long
    val notes = mutableListOf<String>()
    private val metrics = mutableListOf<WidgetMetric>()
    private val dashboard = mutableListOf<WidgetMetric>()
    var training = WidgetTraining()
    lateinit var building: JSONObject
        private set
    private var storedAt: Long? = null

    init {
        require(status.getString("uid") == uid)
        serverNow = data.getLong("currentTs").also { require(it in 1..32_503_680_000L) }
    }

    fun localTime(seconds: Long): Long = receivedAt + (seconds - serverNow).coerceAtLeast(0) * 1000
    fun nextTime(values: List<Long>) = values.filter { it > 0 }.minOrNull()?.let(::localTime)

    fun read(): WidgetSnapshot {
        metrics += sanity()
        building = data.getJSONObject("building")
        storedAt = status.optLong("storeTs", -1).takeIf { it in 1..32_503_680_000L }
        metrics += drones()
        optional("订单进度") { trading() }
        optional("制造进度") { manufacture() }
        optional("公开招募") { recruit() }
        optional("干员疲劳") { WidgetMetric("干员疲劳", building.getJSONArray("tiredChars").length()) }
        optional("每日任务") { cycleMetric("每日任务", data.getJSONObject("routine").getJSONObject("daily"), "每日任务") }
        optional("合成玉") { cycleMetric("合成玉", data.getJSONObject("campaign").getJSONObject("reward"), "每周报酬合成玉") }
        optional("训练室") { trainingRoom() }
        optional("休息") { dormitories() }
        optional("保全酬劳") { towerReward() }
        readDashboard()
        val avatar = OfficialAvatars.safe(status.optJSONObject("avatar")?.optString("url"))
        return WidgetSnapshot(status.getString("name").take(80), metrics, receivedAt, notes, serverNow * 1000,
            avatar, training, dashboardMetrics = dashboard)
    }

    private fun sanity(): WidgetMetric {
        val ap = status.getJSONObject("ap")
        val maxAp = ap.getInt("max").also { require(it in 1..10000) }
        val storedAp = ap.getInt("current").also { require(it in 0..100000) }
        val recovery = ap.optLong("completeRecoveryTime", -1).also { require(it in -1..32_503_680_000L) }
        val currentAp = if (storedAp >= maxAp || recovery <= 0) storedAp else
            (maxAp - ((recovery - serverNow).coerceAtLeast(0) + 359) / 360).coerceIn(storedAp.toLong(), maxAp.toLong()).toInt()
        if (currentAp < maxAp && recovery <= 0) notes.add("理智恢复时间未知")
        val recoveryAt = when {
            storedAp >= maxAp -> 0
            recovery > 0 -> localTime(recovery)
            else -> null
        }
        return WidgetMetric("理智", currentAp, maxAp, recoveryAt, estimated = currentAp != storedAp)
    }

    private fun drones(): WidgetMetric {
        val labor = building.getJSONObject("labor")
        val maxLabor = labor.getInt("maxValue").also { require(it in 1..10000) }
        val storedLabor = labor.getInt("value").also { require(it in 0..maxLabor) }
        val remain = labor.getLong("remainSecs").also { require(it in 0..31_536_000) }
        val updated = labor.getLong("lastUpdateTime").also { require(it in 0..32_503_680_000L) }
        val elapsed = (serverNow - updated).coerceAtLeast(0)
        val currentLabor = if (remain == 0L || storedLabor == maxLabor) maxLabor else
            (storedLabor + elapsed.coerceAtMost(remain) * (maxLabor - storedLabor) / remain).toInt()
        return WidgetMetric("无人机", currentLabor, maxLabor,
            if (currentLabor == maxLabor) 0 else localTime(updated + remain), estimated = currentLabor != storedLabor)
    }

    private fun optional(label: String, block: () -> WidgetMetric) {
        try { metrics.add(block()) }
        catch (_: org.json.JSONException) { notes.add("$label 未提供") }
        catch (_: IllegalArgumentException) { notes.add("$label 未提供") }
    }

    // Fresh HTTP data may still contain last-cycle values; storeTs identifies that cycle.
    fun progress(value: JSONObject, label: String): Pair<Int, Int> {
        val current = value.getInt("current").also { require(it in 0..1000000) }
        val total = value.getInt("total").also { require(it in current..1000000) }
        val stored = storedAt
        val expired = stored != null && cafe.miafetta.tsuki.core.nextDashboardReset(label, stored * 1000) <= serverNow * 1000
        return (if (expired) 0 else current) to total
    }

    private fun cycleMetric(label: String, value: JSONObject, cycle: String): WidgetMetric {
        val (current, total) = progress(value, cycle)
        return WidgetMetric(label, current, total.takeIf { it > 0 }, text = if (total == 0) "$current/0" else null)
    }

    private fun towerReward(): WidgetMetric {
        val reward = data.getJSONObject("tower").getJSONObject("reward")
        val (higher, higherTotal) = progress(reward.getJSONObject("higherItem"), "数据增补仪")
        val (lower, lowerTotal) = progress(reward.getJSONObject("lowerItem"), "数据增补条")
        val total = higherTotal + lowerTotal
        return WidgetMetric("保全酬劳", higher + lower, total.takeIf { it > 0 }, text = if (total == 0) "0/0" else null)
    }

    private fun readDashboard() {
        dashboardProgress("每周任务", data.optJSONObject("routine")?.optJSONObject("weekly"))
        val reward = data.optJSONObject("tower")?.optJSONObject("reward")
        dashboardProgress("数据增补仪", reward?.optJSONObject("higherItem"))
        dashboardProgress("数据增补条", reward?.optJSONObject("lowerItem"))
        dashboardOptional {
            val hire = building.getJSONObject("hire")
            val count = hire.getInt("refreshCount").also { require(it in 0..3) }
            val finish = hire.optLong("completeWorkTime", -1).also { require(it in -1..32_503_680_000L) }
            WidgetMetric("公招刷新", count, 3, if (count < 3 && finish > 0) localTime(finish) else null, MetricTimer.NEXT_COMPLETION)
        }
        dashboardOptional {
            val meeting = building.getJSONObject("meeting")
            val needReceive = meeting.getJSONObject("clue").getInt("needReceive").also { require(it in 0..7) }
            val finish = meeting.optLong("completeWorkTime", -1).also { require(it in -1..32_503_680_000L) }
            WidgetMetric("线索收集", needReceive, 7, recoveryAt = if (finish > 0) localTime(finish) else null,
                timer = MetricTimer.NEXT_COMPLETION)
        }
    }

    private fun dashboardProgress(label: String, value: JSONObject?) {
        if (value == null) return
        dashboardOptional {
            val (current, total) = progress(value, label)
            WidgetMetric(label, current, total.takeIf { it > 0 }, text = if (total == 0) "$current/0" else null)
        }
    }

    // Missing or invalid dashboard-only fields stay unknown without a note.
    private fun dashboardOptional(block: () -> WidgetMetric) {
        try { dashboard.add(block()) }
        catch (_: org.json.JSONException) { /* Missing fields stay unknown. */ }
        catch (_: IllegalArgumentException) { /* Invalid fields stay unknown. */ }
    }
}
