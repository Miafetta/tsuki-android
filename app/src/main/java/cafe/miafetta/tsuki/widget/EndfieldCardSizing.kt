package cafe.miafetta.tsuki.widget

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.RemoteViews
import cafe.miafetta.tsuki.R

// One Endfield row: its cell, decorations, icon, label, value and progress line (endfield_widget.xml).
internal class EndfieldRow(val metricLabel: String, val text: Int, val cell: Int, val dots: Int, val info: Int, val icon: Int,
    val label: Int, val value: Int, val progress: Int, val fill: Int, val loadingLabel: Int, val loadingValue: Int)

// Rows in display order. The metric labels are the snapshot's (the realtime page's names); the shown labels are the
// official widget's, from strings.xml.
internal val endfieldRows = listOf(
    EndfieldRow("活跃度", R.string.endfield_label_daily, R.id.endfield_daily_cell, R.id.endfield_daily_dots, R.id.endfield_daily_info,
        R.id.endfield_daily_icon, R.id.endfield_daily_label, R.id.endfield_daily, R.id.endfield_daily_progress, R.id.endfield_daily_fill,
        R.id.endfield_loading_daily_label, R.id.endfield_loading_daily),
    EndfieldRow("每周事务", R.string.endfield_label_weekly, R.id.endfield_weekly_cell, R.id.endfield_weekly_dots, R.id.endfield_weekly_info,
        R.id.endfield_weekly_icon, R.id.endfield_weekly_label, R.id.endfield_weekly, R.id.endfield_weekly_progress,
        R.id.endfield_weekly_fill, R.id.endfield_loading_weekly_label, R.id.endfield_loading_weekly),
    EndfieldRow("通行证", R.string.endfield_label_pass, R.id.endfield_pass_cell, R.id.endfield_pass_dots, R.id.endfield_pass_info,
        R.id.endfield_pass_icon, R.id.endfield_pass_label, R.id.endfield_pass, R.id.endfield_pass_progress, R.id.endfield_pass_fill,
        R.id.endfield_loading_pass_label, R.id.endfield_loading_pass),
)

// Reference sizes of the card (360dp wide), the same as gen_endfield_layouts.py writes into the layouts.
private object EndfieldGeometry {
    const val BAND = 30f
    const val SIDE = 9f
    const val TOP = 5.4f
    const val BOTTOM = 5.4f
    const val BODY_GAP = 3f
    const val PANEL_GAP = 6.48f
    const val ROWS_SIDE = 9.72f
    const val ROWS_VERTICAL = 3.6f
    const val DIVIDER = 0.9f
    const val ICON = 23.4f
    const val ICON_HEIGHT = 15.6f
    const val ICON_GAP = 6.48f
    const val INDENT = ICON + ICON_GAP
    const val PROGRESS = 2.52f
    const val PROGRESS_GAP = 3.24f
    const val STRIPE = 15.12f
    // The band's two images at its width: the marks (96×119 px) under the top edge, the wordmark (96×258 px) clear of the
    // round bottom corner, so neither is cropped whatever the card's height.
    const val BAND_MARKS = 37.19f
    const val BAND_MARKS_TOP = 5.4f
    const val BAND_WORD = 80.63f
    const val BAND_WORD_BOTTOM = 11f
    // The header marks image (widget_endfield_brand.png, 299×48 px).
    const val BRAND_WIDTH = 82.22f
    const val BRAND_HEIGHT = 13.2f
}

private val stripeIds = listOf(R.id.endfield_stripe_magenta, R.id.endfield_stripe_yellow, R.id.endfield_stripe_cyan)

// The frame both Endfield cards share: band, insets, header, panels, rows and the stripe.
internal fun sizeEndfieldFrame(sizer: CardSizer) = with(EndfieldGeometry) {
    sizer.width(R.id.endfield_band, BAND)
    sizer.size(R.id.endfield_band_marks, BAND, BAND_MARKS)
    sizer.margin(R.id.endfield_band_marks, RemoteViews.MARGIN_TOP, BAND_MARKS_TOP)
    sizer.size(R.id.endfield_band_word, BAND, BAND_WORD)
    sizer.margin(R.id.endfield_band_word, RemoteViews.MARGIN_BOTTOM, BAND_WORD_BOTTOM)
    sizer.views.setViewPadding(R.id.endfield_main, sizer.px(SIDE), sizer.px(TOP), sizer.px(SIDE), sizer.px(BOTTOM))
    sizer.height(R.id.status_header, CardSizer.HEADER)
    sizer.size(R.id.endfield_brand_mark, BRAND_WIDTH, BRAND_HEIGHT)
    sizer.size(R.id.status_refresh_arrow, 12.6f)
    sizer.padding(R.id.status_refresh_hit, 7.2f, 0f)
    sizer.margin(R.id.endfield_body, RemoteViews.MARGIN_TOP, BODY_GAP)
    sizer.margin(R.id.status_sanity_primary, RemoteViews.MARGIN_END, PANEL_GAP)
    sizer.views.setViewPadding(R.id.endfield_rows, sizer.px(ROWS_SIDE), sizer.px(ROWS_VERTICAL), sizer.px(ROWS_SIDE),
        sizer.px(ROWS_VERTICAL))
    for (id in listOf(R.id.endfield_divider_1, R.id.endfield_divider_2)) sizer.height(id, DIVIDER)
    for (row in endfieldRows) {
        sizer.size(row.dots, 21.6f, 1.8f)
        sizer.margin(row.dots, RemoteViews.MARGIN_START, INDENT)
        sizer.views.setViewPadding(row.cell, 0, sizer.px(2.88f), 0, 0)
        sizer.margin(row.info, RemoteViews.MARGIN_TOP, 1.8f)
        sizer.size(row.icon, ICON, ICON_HEIGHT)
        sizer.height(row.progress, PROGRESS)
        sizer.margin(row.progress, RemoteViews.MARGIN_TOP, PROGRESS_GAP)
        sizer.margin(row.progress, RemoteViews.MARGIN_START, INDENT)
    }
    sizer.height(R.id.endfield_stripe, 2.88f)
    sizer.margin(R.id.endfield_stripe, RemoteViews.MARGIN_END, 25.2f)
    for (id in stripeIds) sizer.width(id, STRIPE)
}

internal fun sizeEndfieldContent(views: RemoteViews, context: Context, scale: Float) = with(EndfieldGeometry) {
    val sizer = CardSizer(views, context, scale)
    // OEM hosts can allocate less than their reported options; weights must use the actual bounds.
    val matchParent = ViewGroup.LayoutParams.MATCH_PARENT.toFloat()
    views.setViewLayoutWidth(R.id.endfield_content, matchParent, TypedValue.COMPLEX_UNIT_PX)
    views.setViewLayoutHeight(R.id.endfield_content, matchParent, TypedValue.COMPLEX_UNIT_PX)
    sizeEndfieldFrame(sizer)
    sizer.margin(R.id.status_refresh, RemoteViews.MARGIN_START, 2.88f)
    sizer.size(R.id.status_sanity_icon, 50.4f)
    sizer.margin(R.id.status_sanity_icon, RemoteViews.MARGIN_TOP, 5.4f)
    sizer.margin(R.id.status_sanity_icon, RemoteViews.MARGIN_END, 3.6f)
    views.setViewPadding(R.id.endfield_sanity_info, sizer.px(9.72f), sizer.px(10.8f), sizer.px(8.64f), sizer.px(15.12f))
    sizer.margin(R.id.endfield_sanity_label, RemoteViews.MARGIN_TOP, 3.6f)
    sizer.height(R.id.status_sanity_timer_row, 18f)
    sizer.margin(R.id.status_sanity_timer_row, RemoteViews.MARGIN_TOP, 3.6f)
    sizer.padding(R.id.status_sanity_timer_row, 7.2f, 9f)
    sizer.size(R.id.status_sanity_clock, 4.32f)
    sizer.margin(R.id.status_sanity_clock, RemoteViews.MARGIN_END, 4.32f)
    for (row in endfieldRows) sizer.margin(row.label, RemoteViews.MARGIN_START, ICON_GAP)
}

// The loading card at the placed card's sizes, so nothing moves when data replaces the bars.
internal fun sizeEndfieldLoading(views: RemoteViews, context: Context, width: Float, height: Float) = with(EndfieldGeometry) {
    val sizer = CardSizer(views, context, referenceScale(width, height))
    sizeEndfieldFrame(sizer)
    val bars = listOf(R.id.endfield_loading_refresh to (24f to 8f), R.id.endfield_loading_sanity to (52f to 14f),
        R.id.endfield_loading_sanity_label to (26f to 9.6f), R.id.endfield_loading_recovery to (40f to 8.4f),
        R.id.endfield_loading_sanity_time to (48f to 7.2f)) +
        endfieldRows.flatMap { listOf(it.loadingLabel to (46f to 8.4f), it.loadingValue to (28f to 8.4f)) }
    for ((id, size) in bars) sizer.size(id, size.first, size.second)
    sizer.margin(R.id.endfield_loading_refresh, RemoteViews.MARGIN_START, 2.88f)
    sizer.margin(R.id.endfield_loading_sanity_label, RemoteViews.MARGIN_TOP, 5.4f)
}
