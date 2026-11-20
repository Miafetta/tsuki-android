package cafe.miafetta.tsuki.core

enum class WidgetTheme { DARK, LIGHT }
enum class RefreshState { LOADING, READY, ERROR }
enum class MetricTimer { FULL, NEXT_COMPLETION }
enum class RefreshFailure { NETWORK, AUTH_EXPIRED, RATE_LIMIT, CLOCK_SKEW, INVALID_DATA, SERVER, STORAGE }
enum class TrainingState { UNKNOWN, IDLE, TRAINING, PAUSED, COMPLETE }

fun canonicalMetricLabel(label: String) = when (label) {
    "招募" -> "公开招募"
    "订单" -> "订单进度"
    "制造" -> "制造进度"
    "疲劳" -> "干员疲劳"
    "剿灭奖励" -> "合成玉"
    "休息" -> "宿舍休息"
    else -> label
}

// PAUSED is only read back from snapshots stored by earlier versions: a specialisation cannot pause, so it shows as
// unknown. [remainingSecs] was written for PAUSED alone; it is no longer written or shown, and stays only so that those
// snapshots and widget records still read and write back unchanged.
data class WidgetTraining(val state: TrainingState = TrainingState.UNKNOWN, val traineeId: String? = null,
    val name: String? = null, val skill: Int? = null, val endsAt: Long? = null, val avatarUrl: String? = null,
    val remainingSecs: Long? = null) {
    init {
        require(traineeId == null || traineeId.matches(Regex("char_[A-Za-z0-9_]{1,70}")))
        require(name == null || name.isNotBlank() && name.length <= 40 && !name.contains('\n') && !name.contains('\r'))
        require(skill == null || skill in 0..2)
        require(endsAt == null || endsAt >= 0)
        require(remainingSecs == null || remainingSecs in 0..31_536_000)
        require(avatarUrl == null || avatarUrl.length <= 2048)
    }
    fun completedByTime(now: Long) = state == TrainingState.TRAINING && endsAt != null && endsAt <= now
    fun status(now: Long) = when {
        completedByTime(now) -> "预计完成"
        else -> when (state) {
            TrainingState.UNKNOWN, TrainingState.PAUSED -> "未知"
            TrainingState.IDLE -> "空闲中"
            TrainingState.TRAINING -> "训练中"
            TrainingState.COMPLETE -> "已完成"
        }
    }
    fun time(now: Long): String {
        if (state == TrainingState.IDLE || state == TrainingState.COMPLETE || completedByTime(now)) return "0时0分"
        if (state == TrainingState.PAUSED) return "时间未知"
        val minutes = endsAt?.let { ((it - now).coerceAtLeast(0) + 59999) / 60000 } ?: return "时间未知"
        return "${minutes / 60}时${minutes % 60}分"
    }

}

class DataSourceFailure(val reason: RefreshFailure, val retryAt: Long = 0) : java.io.IOException(reason.name)

data class WidgetInstanceConfig(
    val widgetId: Int,
    val providerId: String,
    val featureId: String,
    val accountId: String,
    val characterId: String,
    val theme: WidgetTheme = WidgetTheme.DARK,
    val simulateFailure: Boolean = false,
) {
    init {
        require(widgetId > 0)
        require(listOf(providerId, featureId, accountId, characterId).all { it.isNotBlank() && it.length <= 80 })
    }

    fun sameDataBinding(other: WidgetInstanceConfig) = providerId == other.providerId &&
        featureId == other.featureId && accountId == other.accountId && characterId == other.characterId
}

// [metricId] is the stable identity used by reminders; snapshots saved before 2.2.0 have none (see MetricIds.kt).
data class WidgetMetric(val label: String, val value: Int, val maximum: Int? = null, val recoveryAt: Long? = null,
    val timer: MetricTimer = MetricTimer.FULL, val text: String? = null, val estimated: Boolean = false,
    val metricId: String? = null) {
    init {
        require(label.isNotBlank() && label.length <= 40 && value >= 0)
        require(metricId == null || metricId.matches(Regex("[a-z][a-z0-9_]{0,19}(\\.[a-z0-9_]{1,30}){1,2}")))
        require(maximum == null || maximum > 0)
        require(recoveryAt == null || recoveryAt >= 0)
        require(text == null || text.isNotBlank() && text.length <= 80 && !text.contains('\n') && !text.contains('\r'))
    }

    fun amount() = text ?: ((if (estimated) "≈" else "") + value + (maximum?.let { "/$it" } ?: ""))
    fun display() = "${canonicalMetricLabel(label)} ${amount()}"
}

data class WidgetSnapshot(val title: String, val metrics: List<WidgetMetric>, val fetchedAt: Long,
    val notes: List<String> = emptyList(), val dataAt: Long = fetchedAt,
    val avatarUrl: String? = null, val training: WidgetTraining = WidgetTraining(), val dataTimeKnown: Boolean = true,
    val dashboardMetrics: List<WidgetMetric> = emptyList()) {
    init {
        require(title.isNotBlank() && title.length <= 80)
        require(metrics.size in 1..12 && fetchedAt >= 0)
        require(dataAt >= 0 && notes.size <= 12 && notes.all { it.length <= 120 })
        require(avatarUrl == null || avatarUrl.length <= 2048)
        require(dashboardMetrics.size <= 5 && dashboardMetrics.map { it.label }.distinct().size == dashboardMetrics.size)
    }
}

data class WidgetRecord(val config: WidgetInstanceConfig, val snapshot: WidgetSnapshot? = null,
    val state: RefreshState = RefreshState.LOADING, val failure: RefreshFailure? = null, val retryAt: Long = 0) {
    init { require(retryAt >= 0) }
}

data class WidgetCharacter(val accountId: String, val characterId: String, val label: String)
data class WidgetDefinition(val id: String, val providerId: String, val otherProviders: Set<String> = emptySet())

interface DataSourceAdapter {
    val id: String
    val characters: List<WidgetCharacter>
    fun fetch(config: WidgetInstanceConfig, now: Long): WidgetSnapshot
}

interface WidgetInstanceRepository {
    fun read(id: Int): WidgetRecord?
    fun write(record: WidgetRecord)
    fun delete(id: Int)
    fun remap(oldIds: IntArray, newIds: IntArray)
}
