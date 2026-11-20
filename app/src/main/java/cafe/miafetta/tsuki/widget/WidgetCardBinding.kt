package cafe.miafetta.tsuki.widget

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.TrainingState
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTraining
import cafe.miafetta.tsuki.core.canonicalMetricLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The steps of StatusWidgetRenderer.renderAtSize, in the order the actions are applied.

internal fun CardViews.bindSurface() {
    if (!native) return
    views.setViewVisibility(R.id.status_native_content, View.VISIBLE)
    views.setInt(R.id.status_native_content, "setBackgroundResource", 0)
    if (light) {
        views.setImageViewResource(R.id.status_native_surface, 0)
        views.setImageViewResource(R.id.status_native_surface, R.drawable.widget_instance_light_surface)
        views.setViewVisibility(R.id.status_native_surface, View.GONE)
    }
}

// Colors are resource references resolved when the host applies cached actions, rather than baked in the sender's mode.
internal fun CardViews.bindPalette() {
    val text = if (light) R.color.widget_text_light else R.color.widget_text_dark
    val textIds = listOf(R.id.status_title, R.id.status_sanity, R.id.status_sanity_time, R.id.status_drone, R.id.status_drone_time,
        trainingName, trainingTime) + summaryValueIds + listOf(R.id.status_refresh, R.id.status_data_time) + summaryLabelIds
    for (viewId in textIds) views.setColor(viewId, "setTextColor", text)
    val secondary = if (light) R.color.widget_secondary_light else R.color.widget_secondary_dark
    for (viewId in listOf(R.id.status_sanity_time, R.id.status_drone_time, trainingTime, R.id.status_data_time)) {
        views.setColor(viewId, "setTextColor", secondary)
    }
    views.setColor(R.id.status_data_time, "setTextColor", if (light) R.color.widget_time_light else secondary)
    val accent = if (light) R.color.widget_accent_light else R.color.widget_accent_dark
    views.setColor(R.id.status_refresh, "setTextColor", accent)
    views.setColor(R.id.status_refresh_arrow, "setColorFilter", accent)
    for (viewId in summaryLabelIds) {
        views.setColor(viewId, "setTextColor", if (light) R.color.widget_label_light else R.color.widget_label_dark)
    }
    for (viewId in clockIds) views.setImageViewResource(viewId, if (light) R.drawable.widget_clock_light else R.drawable.widget_clock_dark)
    views.setImageViewResource(R.id.status_sanity_icon, if (light) R.drawable.widget_sanity_light else R.drawable.widget_sanity_dark)
    views.setImageViewResource(R.id.status_drone_icon, if (light) R.drawable.widget_drone_light else R.drawable.widget_drone_dark)
}

internal fun CardViews.bindPrimaryMetrics(snapshot: WidgetSnapshot?, now: Long) {
    val ratio = context.resources.getDimension(R.dimen.miuix_widget_title3) / context.resources.getDimension(R.dimen.miuix_widget_title2)
    for ((label, valueId, timeId) in listOf(Triple("理智", R.id.status_sanity, R.id.status_sanity_time),
        Triple("无人机", R.id.status_drone, R.id.status_drone_time))) {
        val metric = snapshot?.let { cardMetric(it, label) }
        val amount = cardAmount(metric)
        val styled = SpannableString(amount)
        val slash = amount.indexOf('/')
        if (slash >= 0) styled.setSpan(RelativeSizeSpan(ratio), slash, amount.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        views.setTextViewText(valueId, styled)
        views.setTextViewText(timeId, recoveryTimer(metric, now))
    }
}

internal fun CardViews.bindTraining(training: WidgetTraining, now: Long) {
    for (viewId in listOf(trainingName, trainingTime)) {
        views.setInt(viewId, "setBackgroundResource", 0)
        views.setTextViewCompoundDrawables(viewId, 0, 0, 0, 0)
    }
    views.setTextViewText(trainingName, training.name ?: training.status(now))
    val done = training.state == TrainingState.COMPLETE || training.completedByTime(now)
    views.setTextViewText(trainingTime, if (done) training.status(now) else compactTimer(training.time(now)))
}

internal fun CardViews.bindSummary(snapshot: WidgetSnapshot?) {
    cardSecondary(snapshot).forEachIndexed { index, (label, amount) ->
        views.setTextViewText(summaryLabelIds[index], context.getString(label))
        views.setTextViewText(summaryValueIds[index], amount)
    }
    for (viewId in summaryCellIds) {
        views.setInt(viewId, "setBackgroundResource", if (light) R.drawable.widget_cell_light else R.drawable.widget_cell_dark)
    }
    for (viewId in summaryExtraCellIds) {
        views.setInt(viewId, "setBackgroundResource", if (light) R.drawable.widget_extra_light else R.drawable.widget_extra_dark)
    }
}

internal fun CardViews.bindProgress(snapshot: WidgetSnapshot?) {
    for ((label, viewId) in listOf("合成玉" to R.id.status_jade_progress, "保全酬劳" to R.id.status_tower_progress)) {
        val level = progressLevel(snapshot?.let { cardMetric(it, label) })
        if (native) {
            val fillId = if (viewId == R.id.status_jade_progress) R.id.status_jade_fill else R.id.status_tower_fill
            // Keep progress geometry in basic layouts, independent of the host's ProgressBar styling.
            views.setViewVisibility(fillId, View.GONE)
            views.setInt(viewId, "setLayoutDirection", View.LAYOUT_DIRECTION_LTR)
            views.setFloat(viewId, "setWeightSum", 10000f / (level ?: 0).coerceAtLeast(1))
            views.setColor(viewId, "setBackgroundColor", if (light) R.color.widget_track_light else R.color.widget_track_dark)
            views.setColor(fillId, "setBackgroundColor", if (light) R.color.widget_progress_light else R.color.widget_progress_dark)
            views.setViewVisibility(fillId, if ((level ?: 0) > 0) View.VISIBLE else View.GONE)
        } else {
            views.setImageViewResource(viewId, if (light) R.drawable.widget_progress_light else R.drawable.widget_progress_dark)
            views.setInt(viewId, "setImageLevel", level ?: 0)
        }
        views.setViewVisibility(viewId, if (level == null) View.INVISIBLE else View.VISIBLE)
    }
}

// Sets the header time and the whole-card description; returns the full data time used by other descriptions.
internal fun CardViews.bindStatus(snapshot: WidgetSnapshot?, training: WidgetTraining, now: Long, status: String,
    failure: String): String {
    val known = snapshot?.takeIf { it.dataTimeKnown }
    val time = known?.let { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.dataAt)) } ?: "数据时间未知"
    val headerTime = known?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.dataAt)) } ?: "时间未知"
    views.setTextViewText(R.id.status_data_time, headerTime)
    val estimated = snapshot?.metrics?.filter { it.estimated }?.takeIf { it.isNotEmpty() }
        ?.joinToString("，", prefix = "本地估算：") { canonicalMetricLabel(it.label) }
    views.setContentDescription(android.R.id.background, listOfNotNull(snapshot?.title,
        snapshot?.metrics?.joinToString("，") { "${canonicalMetricLabel(it.label)} ${cardAmount(it)}" },
        trainingDescription(training, now), "宿舍休息按同步心情、宿舍回复速率与时间计算", estimated,
        snapshot?.notes?.joinToString("，"), status.takeIf { it.isNotEmpty() },
        failure.takeIf { it.isNotEmpty() }, "数据时间 $time").joinToString("。"))
    return time
}

// Default Miuix type hierarchy is mapped once, then follows the whole widget's geometry.
internal fun CardViews.bindTextSizes(textScale: Float) {
    val textSizes = listOf(R.id.status_title to R.dimen.miuix_widget_title2,
        R.id.status_data_time to R.dimen.widget_header_text, R.id.status_refresh to R.dimen.widget_header_text,
        R.id.status_sanity to R.dimen.miuix_widget_title2, R.id.status_drone to R.dimen.miuix_widget_title2,
        trainingName to R.dimen.miuix_widget_title3,
        R.id.status_sanity_time to R.dimen.miuix_widget_body1, R.id.status_drone_time to R.dimen.miuix_widget_body1,
        trainingTime to R.dimen.miuix_widget_body1)
    val units = TypedValue.COMPLEX_UNIT_DIP
    fun size(resource: Int) = context.resources.getDimension(resource) / context.resources.displayMetrics.density * textScale
    for ((viewId, resource) in textSizes) views.setTextViewTextSize(viewId, units, size(resource))
    for (viewId in summaryValueIds) views.setTextViewTextSize(viewId, units, size(R.dimen.miuix_widget_body2))
    for (viewId in summaryLabelIds) views.setTextViewTextSize(viewId, units, size(R.dimen.miuix_widget_footnote1))
}

// The native layout describes each region separately instead of one whole-card description.
internal fun CardViews.bindNativeDescriptions(snapshot: WidgetSnapshot?, training: WidgetTraining, now: Long, timeParts: List<String>) {
    views.setContentDescription(android.R.id.background, null)
    for ((label, viewId) in listOf("理智" to R.id.status_sanity, "无人机" to R.id.status_drone)) {
        val metric = snapshot?.let { cardMetric(it, label) }
        views.setContentDescription(viewId, "$label ${cardAmount(metric)}，恢复 ${recoveryTimer(metric, now)}")
    }
    views.setContentDescription(trainingName, trainingDescription(training, now))
    cardSecondary(snapshot).forEachIndexed { index, (label, amount) ->
        views.setContentDescription(summaryValueIds[index], "${context.getString(label)} $amount")
    }
    views.setContentDescription(R.id.status_data_time, timeParts.filter { it.isNotEmpty() }.joinToString("。"))
}

private fun trainingDescription(training: WidgetTraining, now: Long) =
    "训练室 ${training.status(now)}，${training.name ?: "干员未知"}，${training.time(now)}"
