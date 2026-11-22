package cafe.miafetta.tsuki.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.core.WidgetSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The Endfield card (endfield_widget_v3), aligned to the official Android Skland Endfield widget and always dark.
// Shares the Arknights card's refresh hit area, actions, status texts and per-size rendering.
internal object EndfieldCard {
    fun render(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean, width: Float, height: Float,
        snapshotOverride: WidgetSnapshot? = null): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.endfield_widget_v3)
        val snapshot = snapshotOverride ?: record?.snapshot
        val now = System.currentTimeMillis()
        val sanity = snapshot?.let { cardMetric(it, "理智") }
        val sanityFull = isSanityFull(sanity, now)
        bindPalette(views, sanityFull)
        bindSanity(views, sanity)
        val unconfigured = record == null && snapshotOverride == null && !storageError
        val pill = if (unconfigured) context.getString(R.string.endfield_tap_configure) else sanityTimer(context, sanity, now)
        views.setTextViewText(R.id.status_sanity_time, pill)
        val recovering = !unconfigured && !sanityFull && sanity?.recoveryAt?.let { it > now } == true
        views.setImageViewResource(R.id.status_sanity_clock,
            if (recovering) R.drawable.widget_endfield_dot_green else R.drawable.widget_endfield_dot)
        for (row in endfieldRows) bindRow(context, views, row, snapshot)
        val status = cardStatus(record, storageError)
        val failure = record?.failure?.let { context.getString(cafe.miafetta.tsuki.failureMessage(it.name)) }.orEmpty()
        val known = snapshot?.takeIf { it.dataTimeKnown }
        val time = known?.let { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.dataAt)) } ?: "数据时间未知"
        val rows = endfieldRows.joinToString("，") { row ->
            "${context.getString(row.text)} ${cardAmount(snapshot?.let { cardMetric(it, row.metricLabel) })}"
        }
        val marks = context.getString(R.string.endfield_brand_platform) + " " + context.getString(R.string.endfield_brand_game)
        views.setContentDescription(android.R.id.background, listOfNotNull(marks,
            snapshot?.title ?: context.getString(R.string.endfield_unconfigured),
            "理智 ${cardAmount(sanity)}，$pill", rows, snapshot?.notes?.takeIf { it.isNotEmpty() }?.joinToString("，"),
            status.takeIf { it.isNotEmpty() }, failure.takeIf { it.isNotEmpty() }, "数据时间 $time").joinToString("。"))
        bindWidgetActions(context, id, views, widgetTapTarget(record, storageError))
        views.setContentDescription(R.id.status_refresh_hit,
            listOf("刷新", status, "数据时间 $time").filter { it.isNotEmpty() }.joinToString("。"))
        val scale = referenceScale(width, height)
        bindTextSizes(context, views, scale)
        sizeEndfieldContent(views, context, scale)
        return views
    }

    fun loading(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean, width: Float, height: Float): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.endfield_widget_loading)
        sizeEndfieldLoading(views, context, width, height)
        bindWidgetActions(context, id, views, widgetTapTarget(record, storageError))
        return views
    }

    // Official widget shows sanity yellow when full and white while recovering, using separate views so HyperOS keeps every tone.
    private fun bindPalette(views: RemoteViews, sanityFull: Boolean) {
        val text = R.color.widget_endfield_text
        val rowText = R.color.widget_endfield_row_text
        val secondary = R.color.widget_endfield_secondary
        val slash = R.color.widget_endfield_slash
        views.setColor(R.id.status_sanity, "setTextColor", if (sanityFull) R.color.widget_endfield_yellow else text)
        views.setColor(R.id.endfield_sanity_slash, "setTextColor", slash)
        views.setColor(R.id.endfield_sanity_max, "setTextColor", secondary)
        views.setColor(R.id.endfield_sanity_label, "setTextColor", text)
        views.setColor(R.id.endfield_recovery_label, "setTextColor", secondary)
        views.setColor(R.id.status_sanity_time, "setTextColor", rowText)
        views.setColor(R.id.status_refresh, "setTextColor", R.color.widget_endfield_refresh_text)
        for (row in endfieldRows) {
            views.setColor(row.label, "setTextColor", rowText)
            views.setColor(row.value, "setTextColor", text)
            views.setColor(row.slash, "setTextColor", slash)
            views.setColor(row.maximum, "setTextColor", secondary)
        }
    }

    private fun bindSanity(views: RemoteViews, sanity: WidgetMetric?) {
        if (sanity == null) {
            views.setTextViewText(R.id.status_sanity, "未知")
            views.setViewVisibility(R.id.endfield_sanity_slash, View.GONE)
            views.setViewVisibility(R.id.endfield_sanity_max, View.GONE)
            return
        }
        views.setTextViewText(R.id.status_sanity, sanity.text ?: sanity.value.toString())
        val max = sanity.maximum?.takeIf { sanity.text == null }
        views.setViewVisibility(R.id.endfield_sanity_slash, if (max != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.endfield_sanity_max, if (max != null) View.VISIBLE else View.GONE)
        if (max != null) views.setTextViewText(R.id.endfield_sanity_max, max.toString())
    }

    private fun bindRow(context: Context, views: RemoteViews, row: EndfieldRow, snapshot: WidgetSnapshot?) {
        val metric = snapshot?.let { cardMetric(it, row.metricLabel) }
        views.setTextViewText(row.label, context.getString(row.text))
        if (metric == null) {
            views.setTextViewText(row.value, "未知")
            views.setViewVisibility(row.slash, View.GONE)
            views.setViewVisibility(row.maximum, View.GONE)
            return
        }
        views.setTextViewText(row.value, metric.text ?: metric.value.toString())
        val max = metric.maximum?.takeIf { metric.text == null }
        views.setViewVisibility(row.slash, if (max != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(row.maximum, if (max != null) View.VISIBLE else View.GONE)
        if (max != null) views.setTextViewText(row.maximum, max.toString())
    }

    private fun isSanityFull(metric: WidgetMetric?, now: Long): Boolean {
        if (metric == null) return false
        if (metric.maximum != null && metric.value >= metric.maximum) return true
        return metric.recoveryAt?.let { it <= now } == true
    }

    // Official widget shows "理智已达上限" once full, and "Xh Ymin" / "Ymin" before that.
    private fun sanityTimer(context: Context, metric: WidgetMetric?, now: Long): String {
        val at = metric?.recoveryAt ?: return if (metric?.maximum != null && metric.value >= metric.maximum) {
            context.getString(R.string.endfield_sanity_full)
        } else "时间未知"
        return if (at <= now) context.getString(R.string.endfield_sanity_full) else endfieldRecoveryText(at - now)
    }

    private fun bindTextSizes(context: Context, views: RemoteViews, scale: Float) {
        val units = TypedValue.COMPLEX_UNIT_DIP
        fun size(resource: Int) = context.resources.getDimension(resource) / context.resources.displayMetrics.density * scale
        val sizes = listOf(R.id.status_refresh to R.dimen.widget_endfield_brand,
            R.id.status_sanity to R.dimen.widget_endfield_amount,
            R.id.endfield_sanity_slash to R.dimen.widget_endfield_amount_max,
            R.id.endfield_sanity_max to R.dimen.widget_endfield_amount_max,
            R.id.endfield_sanity_label to R.dimen.widget_endfield_sanity_label,
            R.id.endfield_recovery_label to R.dimen.widget_endfield_caption,
            R.id.status_sanity_time to R.dimen.widget_endfield_pill) +
            endfieldRows.flatMap {
                listOf(it.label to R.dimen.widget_endfield_row, it.value to R.dimen.widget_endfield_value,
                    it.slash to R.dimen.widget_endfield_value, it.maximum to R.dimen.widget_endfield_value)
            }
        for ((viewId, resource) in sizes) views.setTextViewTextSize(viewId, units, size(resource))
    }
}

// Official Endfield recovery format: "Xh Ymin" when an hour or more remains, otherwise "Ymin" (minimum 1min before full).
internal fun endfieldRecoveryText(millis: Long): String {
    val minutes = ((millis.coerceAtLeast(0) + 59_999) / 60_000).coerceAtLeast(1)
    val hours = minutes / 60
    val rem = minutes % 60
    return if (hours > 0) "${hours}h ${rem}min" else "${rem}min"
}
