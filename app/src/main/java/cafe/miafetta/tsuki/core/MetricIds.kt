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

// [arknightsSource] says whether the metric comes from an Arknights data source (the Skland adapter or the debug
// fixture). The caller decides, because core does not know data source IDs (docs/PROJECT_STRUCTURE.md). Without it a
// metric that has no ID stays unidentified: Endfield widget records written by 2.2.0 and 2.2.1 have none until their
// next refresh, and their "理智" must not become arknights.sanity.
internal fun WidgetMetric.stableId(arknightsSource: Boolean): String? =
    metricId ?: if (arknightsSource) arknightsMetricIds[canonicalMetricLabel(label)] else null
