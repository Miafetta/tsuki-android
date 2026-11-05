package cafe.miafetta.tsuki.widget

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.RemoteViews
import cafe.miafetta.tsuki.R

// Summary cells in cardSecondary order: values, their labels and their backgrounds.
internal val summaryValueIds = listOf(R.id.status_recruit, R.id.status_fatigue, R.id.status_orders, R.id.status_jade,
    R.id.status_daily, R.id.status_rest, R.id.status_manufacture, R.id.status_tower)
internal val summaryLabelIds = listOf(R.id.status_recruit_label, R.id.status_fatigue_label, R.id.status_orders_label,
    R.id.status_jade_label, R.id.status_daily_label, R.id.status_rest_label, R.id.status_manufacture_label, R.id.status_tower_label)
internal val summaryCellIds = listOf(R.id.status_recruit_cell, R.id.status_fatigue_cell, R.id.status_orders_cell,
    R.id.status_manufacture_cell)
internal val summaryExtraCellIds = listOf(R.id.status_daily_cell, R.id.status_rest_cell)
internal val clockIds = listOf(R.id.status_sanity_clock, R.id.status_drone_clock, R.id.status_training_clock)

// Scales every fixed dimension of the native layout from its 360dp reference to the actual widget size.
internal fun sizeNativeContent(views: RemoteViews, context: Context, scale: Float) {
    val sizer = CardSizer(views, context, scale)
    // OEM hosts can allocate less than their reported options; weights must use the actual bounds.
    val matchParent = ViewGroup.LayoutParams.MATCH_PARENT.toFloat()
    views.setViewLayoutWidth(R.id.status_native_content, matchParent, TypedValue.COMPLEX_UNIT_PX)
    views.setViewLayoutHeight(R.id.status_native_content, matchParent, TypedValue.COMPLEX_UNIT_PX)
    sizeCardFrame(sizer, R.id.status_native_content, R.id.status_native_training_avatar)
    for (id in listOf(R.id.status_title, R.id.status_data_time)) sizer.margin(id, RemoteViews.MARGIN_START, CardSizer.TITLE_GAP)
    views.setInt(R.id.status_data_time, "setMinWidth", sizer.px(CardSizer.DATA_TIME_WIDTH))
    for (id in summaryCellIds + summaryExtraCellIds + listOf(R.id.status_jade_cell, R.id.status_tower_cell)) {
        sizer.height(id, CardSizer.ROW)
    }
    for (id in summaryValueIds) sizer.margin(id, RemoteViews.MARGIN_START, CardSizer.VALUE_GAP)
}

// The frame the native and loading cards share: inset, header, the three primary columns and the summary rows and cells.
// `portraitId` is the training portrait, a picture on the native card and a placeholder shape on the loading card.
internal fun sizeCardFrame(sizer: CardSizer, contentId: Int, portraitId: Int) {
    val inset = sizer.px(CardSizer.INSET)
    sizer.views.setViewPadding(contentId, inset, inset, inset, inset)
    sizer.height(R.id.status_header, CardSizer.HEADER)
    sizer.size(R.id.status_avatar, CardSizer.HEADER)
    sizer.size(R.id.status_refresh_arrow, 12f)
    sizer.margin(R.id.status_refresh_hit, RemoteViews.MARGIN_START, 6.84f)
    sizer.padding(R.id.status_refresh_hit, 3.6f, 0f)
    sizer.margin(R.id.status_refresh_arrow, RemoteViews.MARGIN_START, 2.88f)
    sizePrimaryFrame(sizer, portraitId)
    sizeSummaryFrame(sizer)
}

private fun sizePrimaryFrame(sizer: CardSizer, portraitId: Int) {
    sizer.margin(R.id.status_primary, RemoteViews.MARGIN_TOP, CardSizer.PRIMARY_GAP)
    sizer.margin(R.id.status_primary, RemoteViews.MARGIN_BOTTOM, CardSizer.PRIMARY_GAP)
    for (id in listOf(R.id.status_sanity_primary, R.id.status_drone_primary)) sizer.margin(id, RemoteViews.MARGIN_END, 10.8f)
    for (id in listOf(R.id.status_sanity_icon, R.id.status_drone_icon)) sizer.size(id, 29.52f, 34.2f)
    sizer.size(portraitId, 34.2f)
    for (id in listOf(R.id.status_sanity_icon, R.id.status_drone_icon, portraitId)) sizer.margin(id, RemoteViews.MARGIN_END, 5.4f)
    for (id in listOf(R.id.status_sanity_timer_row, R.id.status_drone_timer_row, R.id.status_training_timer_row)) {
        sizer.margin(id, RemoteViews.MARGIN_TOP, 3.24f)
    }
    for (id in clockIds) {
        sizer.size(id, 7.2f)
        sizer.margin(id, RemoteViews.MARGIN_END, 2.88f)
    }
}

private fun sizeSummaryFrame(sizer: CardSizer) {
    sizer.height(R.id.status_summary, CardSizer.SUMMARY)
    for (id in listOf(R.id.status_summary_row_first, R.id.status_summary_row_second)) sizer.height(id, CardSizer.ROW)
    sizer.margin(R.id.status_summary_row_second, RemoteViews.MARGIN_TOP, 4.32f)
    for (id in summaryCellIds + summaryExtraCellIds) {
        sizer.margin(id, RemoteViews.MARGIN_END, 5.76f)
        sizer.padding(id, 3.6f)
    }
    for (id in listOf(R.id.status_jade_info, R.id.status_tower_info)) sizer.height(id, CardSizer.INFO)
    for (id in listOf(R.id.status_jade_progress, R.id.status_tower_progress)) {
        sizer.height(id, CardSizer.PROGRESS)
        sizer.margin(id, RemoteViews.MARGIN_TOP, CardSizer.PROGRESS_GAP)
    }
}
