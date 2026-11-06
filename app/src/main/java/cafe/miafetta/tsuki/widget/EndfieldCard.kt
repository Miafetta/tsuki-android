package cafe.miafetta.tsuki.widget

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.durationWords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The Endfield card (endfield_widget*), after the official 明日方舟：终末地数据工具 and always dark like it. Header marks, sanity
// panel, rows, band and stripe follow the official card; the surface texture and its extra header buttons are not copied.
// It shares the Arknights card's refresh hit area, actions, status texts and per-size rendering.
internal object EndfieldCard {
    fun render(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean, width: Float, height: Float,
        snapshotOverride: WidgetSnapshot? = null): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.endfield_widget)
        val snapshot = snapshotOverride ?: record?.snapshot
        val now = System.currentTimeMillis()
        bindPalette(views)
        val sanity = snapshot?.let { cardMetric(it, "理智") }
        views.setTextViewText(R.id.status_sanity, amount(context, sanity, yellow = true))
        val pill = when {
            record == null && snapshotOverride == null && !storageError -> context.getString(R.string.endfield_tap_configure)
            else -> sanityTimer(context, sanity, now)
        }
        views.setTextViewText(R.id.status_sanity_time, pill)
        for (row in endfieldRows) bindRow(context, views, row, snapshot)
        val status = cardStatus(record, storageError)
        val failure = record?.failure?.let { context.getString(cafe.miafetta.tsuki.failureMessage(it.name)) }.orEmpty()
        val known = snapshot?.takeIf { it.dataTimeKnown }
        val time = known?.let { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.dataAt)) } ?: "数据时间未知"
        // The official header has no data time; it stays in the descriptions.
        val rows = endfieldRows.joinToString("，") { row ->
            "${context.getString(row.text)} ${cardAmount(snapshot?.let { cardMetric(it, row.metricLabel) })}"
        }
        // The header shows the platform and game marks like the official card, so the role is named here.
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

    // Colors are resource references resolved when the host applies cached actions, as on the Arknights card.
    private fun bindPalette(views: RemoteViews) {
        val text = R.color.widget_endfield_text
        val secondary = R.color.widget_endfield_secondary
        val primary = listOf(R.id.endfield_sanity_label, R.id.status_sanity_time) + endfieldRows.flatMap { listOf(it.label, it.value) }
        for (viewId in primary) views.setColor(viewId, "setTextColor", text)
        // The sanity amount is yellow by its text colour too: the HyperOS desktop showed it white with only the span.
        views.setColor(R.id.status_sanity, "setTextColor", R.color.widget_endfield_yellow)
        for (viewId in listOf(R.id.status_refresh, R.id.endfield_recovery_label)) views.setColor(viewId, "setTextColor", secondary)
        views.setColor(R.id.status_refresh_arrow, "setColorFilter", secondary)
    }

    private fun bindRow(context: Context, views: RemoteViews, row: EndfieldRow, snapshot: WidgetSnapshot?) {
        val metric = snapshot?.let { cardMetric(it, row.metricLabel) }
        views.setTextViewText(row.label, context.getString(row.text))
        views.setTextViewText(row.value, amount(context, metric, yellow = false))
        // Progress geometry stays in basic layouts, independent of the host's ProgressBar styling.
        val level = progressLevel(metric)
        views.setInt(row.progress, "setLayoutDirection", View.LAYOUT_DIRECTION_LTR)
        views.setFloat(row.progress, "setWeightSum", 10000f / (level ?: 0).coerceAtLeast(1))
        views.setColor(row.progress, "setBackgroundColor", R.color.widget_endfield_track)
        views.setColor(row.fill, "setBackgroundColor", R.color.widget_endfield_fill)
        views.setViewVisibility(row.fill, if ((level ?: 0) > 0) View.VISIBLE else View.GONE)
        views.setViewVisibility(row.progress, if (level == null) View.INVISIBLE else View.VISIBLE)
    }

    // "125 /125" on the sanity panel (current in yellow, the maximum smaller and grey) and "0 /100" in the rows (current bold).
    private fun amount(context: Context, metric: WidgetMetric?, yellow: Boolean): CharSequence {
        if (metric == null) return "未知"
        val current = metric.text ?: metric.value.toString()
        // The rows keep the maximum in a fixed-width column like the official card ("0  /100", "0  / 10"); figure spaces
        // (U+2007) have a digit's width.
        val maximum = metric.maximum?.takeIf { metric.text == null }?.let {
            if (yellow) " /$it" else "  /" + it.toString().padStart(3, ' ')
        }.orEmpty()
        val styled = SpannableString(current + maximum)
        val resources = context.resources
        if (yellow) styled.setSpan(ForegroundColorSpan(resources.getColor(R.color.widget_endfield_yellow, null)), 0, current.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        else styled.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, current.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (maximum.isNotEmpty()) {
            styled.setSpan(ForegroundColorSpan(resources.getColor(R.color.widget_endfield_secondary, null)), current.length,
                styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (yellow) styled.setSpan(RelativeSizeSpan(resources.getDimension(R.dimen.widget_endfield_amount_max) /
                resources.getDimension(R.dimen.widget_endfield_amount)), current.length, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return styled
    }

    // The official card says "理智已达上限" once full, and the time to full before that.
    private fun sanityTimer(context: Context, metric: WidgetMetric?, now: Long): String {
        val at = metric?.recoveryAt ?: return "时间未知"
        return if (at <= now) context.getString(R.string.endfield_sanity_full) else durationWords(at - now)
    }

    private fun bindTextSizes(context: Context, views: RemoteViews, scale: Float) {
        val units = TypedValue.COMPLEX_UNIT_DIP
        fun size(resource: Int) = context.resources.getDimension(resource) / context.resources.displayMetrics.density * scale
        // The sizes the layouts declare (widget_endfield_dimens.xml), scaled to this widget size.
        val sizes = listOf(R.id.status_refresh to R.dimen.widget_endfield_brand,
            R.id.status_sanity to R.dimen.widget_endfield_amount, R.id.endfield_sanity_label to R.dimen.widget_endfield_sanity_label,
            R.id.endfield_recovery_label to R.dimen.widget_endfield_caption, R.id.status_sanity_time to R.dimen.widget_endfield_pill) +
            endfieldRows.flatMap { listOf(it.label to R.dimen.widget_endfield_row, it.value to R.dimen.widget_endfield_value) }
        for ((viewId, resource) in sizes) views.setTextViewTextSize(viewId, units, size(resource))
    }
}
