package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTheme
import cafe.miafetta.tsuki.core.canonicalMetricLabel
import cafe.miafetta.tsuki.providers.OfficialAvatars

// Text, layout and geometry choices shared by the widget renderer, the app preview and their tests.

internal const val REFERENCE_WIDTH = 360f
internal const val REFERENCE_HEIGHT = REFERENCE_WIDTH * 462f / 1028f

// Metric keys of the eight summary cells in display order. They select metrics and are never shown.
internal val summaryLabels = listOf("公开招募", "干员疲劳", "订单进度", "合成玉", "每日任务", "宿舍休息", "制造进度", "保全酬劳")

// Shown labels of the same cells. strings.xml holds the text once for the renderer and the static picker preview.
internal val summaryLabelTexts = listOf(R.string.status_label_recruit, R.string.status_label_fatigue, R.string.status_label_orders,
    R.string.status_label_jade, R.string.status_label_daily, R.string.status_label_rest, R.string.status_label_manufacture,
    R.string.status_label_tower)

internal fun cardMetric(snapshot: WidgetSnapshot, label: String) = snapshot.metrics.firstOrNull { canonicalMetricLabel(it.label) == label }

internal fun themeLayout(theme: WidgetTheme, modern: Boolean = true) = if (modern) {
    if (theme == WidgetTheme.LIGHT) R.layout.status_widget_scaled_light else R.layout.status_widget_scaled_dark
} else if (theme == WidgetTheme.LIGHT) R.layout.status_widget_light else R.layout.status_widget_dark

internal fun bitmapThemeLayout(theme: WidgetTheme) = if (theme == WidgetTheme.LIGHT)
    R.layout.status_widget_bitmap_light else R.layout.status_widget_bitmap_dark

internal fun loadingLayout(theme: WidgetTheme) = if (theme == WidgetTheme.LIGHT)
    R.layout.status_widget_loading_light else R.layout.status_widget_loading_dark

// Only a first fetch has nothing to show: a cached snapshot, a failure, an unconfigured card and a storage error each
// keep the full card, whose status text explains them.
internal fun showsLoadingPlaceholder(record: WidgetRecord?, storageError: Boolean) =
    !storageError && record != null && record.snapshot == null && record.state == RefreshState.LOADING

internal fun compactTimer(text: String) = Regex("(\\d+)时(\\d+)分").replace(text) { "${it.groupValues[1]}h${it.groupValues[2]}m" }

internal fun recoveryTimer(metric: WidgetMetric?, now: Long): String {
    val target = metric?.recoveryAt ?: return "时间未知"
    val minutes = ((target - now).coerceAtLeast(0) + 59_999) / 60_000
    return "${minutes / 60}h${minutes % 60}m"
}

internal fun progressLevel(metric: WidgetMetric?): Int? = metric?.maximum?.let {
    ((metric.value.toLong() * 10000) / it).coerceIn(0, 10000).toInt()
}

internal fun cardAmount(metric: WidgetMetric?) = metric?.amount()?.removePrefix("≈") ?: "未知"

// Each summary cell as its label string resource and its amount; callers resolve the label with their own resources.
internal fun cardSecondary(snapshot: WidgetSnapshot?): List<Pair<Int, String>> = summaryLabels.zip(summaryLabelTexts) { key, text ->
    text to cardAmount(snapshot?.let { cardMetric(it, key) })
}

internal fun materialTintAlpha(theme: WidgetTheme) = if (theme == WidgetTheme.LIGHT) 230 else 224

internal fun referenceScale(width: Float, height: Float): Float {
    val safeWidth = width.takeIf { it.isFinite() && it in 1f..800f } ?: REFERENCE_WIDTH
    return minOf(safeWidth / REFERENCE_WIDTH, referenceHeight(height) / REFERENCE_HEIGHT)
}

// The height a card is sized for: the host's value when plausible, otherwise the reference height.
internal fun referenceHeight(height: Float) = height.takeIf { it.isFinite() && it in 1f..800f } ?: REFERENCE_HEIGHT

internal fun trainingAvatar(record: WidgetRecord?): String? = record?.snapshot?.training?.let {
    if (it.traineeId != null) it.avatarUrl ?: OfficialAvatars.character(it.traineeId) else null
}

internal fun cardStatus(record: WidgetRecord?, storageError: Boolean = false): String = when {
    storageError -> "存储失败"
    record == null -> "点击配置"
    record.state == RefreshState.LOADING -> "刷新中" + if (record.snapshot != null) "，显示旧缓存" else ""
    record.failure == RefreshFailure.RATE_LIMIT -> ""
    record.state == RefreshState.ERROR -> "刷新失败" + if (record.snapshot != null) "，显示旧缓存" else ""
    else -> "刷新成功"
}
