package cafe.miafetta.tsuki.core

// Arknights metrics were identified by their Chinese label before metric IDs existed, and cached snapshots still are.
// New data sources set WidgetMetric.metricId themselves; labels are never an identity for them, because "理智" exists
// in more than one game.
private val arknightsMetricIds = mapOf(
    "理智" to "arknights.sanity",
    "无人机" to "arknights.drones",
    "公开招募" to "arknights.recruit",
    "公招刷新" to "arknights.recruit_refresh",
    "订单进度" to "arknights.trading",
    "制造进度" to "arknights.manufacture",
    "干员疲劳" to "arknights.tired",
    "每日任务" to "arknights.daily",
    "合成玉" to "arknights.annihilation",
    "训练室" to "arknights.training",
    "宿舍休息" to "arknights.dormitory_rest",
    "保全酬劳" to "arknights.tower",
    "线索收集" to "arknights.clues",
)

internal fun WidgetMetric.stableId(): String? = metricId ?: arknightsMetricIds[canonicalMetricLabel(label)]
