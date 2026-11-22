package cafe.miafetta.tsuki.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import cafe.miafetta.tsuki.R

// A translucent bar standing in for one text field at the 360dp reference: its size, and the top and bottom margin that
// centre it on the line height of the text it replaces.
internal class LoadingBar(val id: Int, val width: Float, val height: Float, val lineMargin: Float = 0f)

private const val TIME_BAR_WIDTH = 28f
private const val SUMMARY_BAR_HEIGHT = 5.6f

// Label and value bars of the summary cells in cardSecondary order; the value margins match the native value text.
internal val loadingLabelIds = listOf(R.id.status_loading_recruit_label, R.id.status_loading_fatigue_label,
    R.id.status_loading_orders_label, R.id.status_loading_jade_label, R.id.status_loading_daily_label,
    R.id.status_loading_rest_label, R.id.status_loading_manufacture_label, R.id.status_loading_tower_label)
internal val loadingValueIds = listOf(R.id.status_loading_recruit, R.id.status_loading_fatigue, R.id.status_loading_orders,
    R.id.status_loading_jade, R.id.status_loading_daily, R.id.status_loading_rest, R.id.status_loading_manufacture,
    R.id.status_loading_tower)

// Jade and tower share their cell with a progress line and show longer values, as in the native card.
private val progressLabelIds = setOf(R.id.status_loading_jade_label, R.id.status_loading_tower_label)

// Every bar of status_widget_loading_*. The XML gives them these sizes as minimums with wrap_content, so the cold initial
// layout shrinks them into small cells; the runtime card then sets them exactly.
internal val loadingBars = listOf(LoadingBar(R.id.status_loading_title, 64f, 10f),
    LoadingBar(R.id.status_loading_time, TIME_BAR_WIDTH, 8f), LoadingBar(R.id.status_loading_refresh, 24f, 8f),
    LoadingBar(R.id.status_loading_sanity, 44f, 10f, 3.5f), LoadingBar(R.id.status_loading_sanity_time, 30f, 6.4f, 2.4f),
    LoadingBar(R.id.status_loading_drone, 44f, 10f, 3.5f), LoadingBar(R.id.status_loading_drone_time, 30f, 6.4f, 2.4f),
    LoadingBar(R.id.status_loading_training, 40f, 8.4f, 2.9f), LoadingBar(R.id.status_loading_training_time, 30f, 6.4f, 2.4f)) +
    loadingLabelIds.zip(loadingValueIds).flatMap { (label, value) ->
        val progress = label in progressLabelIds
        listOf(LoadingBar(label, if (progress) 20f else 28f, SUMMARY_BAR_HEIGHT),
            LoadingBar(value, if (progress) 24f else 12f, SUMMARY_BAR_HEIGHT))
    }

// Sizes the loading card exactly like the native card of the same size, so the frame stays put when data replaces the
// placeholders. The loading layout weights its rows so that the XML alone fits any cell before the first update; with the
// exact heights set here those weights only absorb the pixel or two the host's rounding leaves over.
internal fun sizeLoadingContent(views: RemoteViews, context: Context, width: Float, height: Float) {
    val sizer = CardSizer(views, context, referenceScale(width, height))
    sizeCardFrame(sizer, R.id.status_loading_content, R.id.status_loading_trainee)
    // The native primary row takes what the inset, header and summary leave; give it the same pixels.
    val primary = (referenceHeight(height) * sizer.density).toInt() - 2 * sizer.px(CardSizer.INSET) -
        sizer.sizePx(CardSizer.HEADER) - sizer.sizePx(CardSizer.SUMMARY) - 2 * sizer.offsetPx(CardSizer.PRIMARY_GAP)
    views.setViewLayoutHeight(R.id.status_primary, primary.coerceAtLeast(0).toFloat(), TypedValue.COMPLEX_UNIT_PX)
    for (bar in loadingBars) {
        sizer.size(bar.id, bar.width, bar.height)
        if (bar.lineMargin == 0f) continue
        sizer.margin(bar.id, RemoteViews.MARGIN_TOP, bar.lineMargin)
        sizer.margin(bar.id, RemoteViews.MARGIN_BOTTOM, bar.lineMargin)
    }
    for (id in listOf(R.id.status_loading_title, R.id.status_loading_time)) {
        sizer.margin(id, RemoteViews.MARGIN_START, CardSizer.TITLE_GAP)
    }
    // The time bar and its end margin span the native time field's minimum width.
    sizer.margin(R.id.status_loading_time, RemoteViews.MARGIN_END, CardSizer.DATA_TIME_WIDTH - TIME_BAR_WIDTH)
    for (id in loadingValueIds) sizer.margin(id, RemoteViews.MARGIN_START, CardSizer.VALUE_GAP)
    // The native progress cells leave a strip under the line; keep it so the weighted info row does not take it.
    val strip = CardSizer.ROW - CardSizer.INFO - CardSizer.PROGRESS - CardSizer.PROGRESS_GAP
    for (id in listOf(R.id.status_jade_progress, R.id.status_tower_progress)) sizer.margin(id, RemoteViews.MARGIN_BOTTOM, strip)
}
