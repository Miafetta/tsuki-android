package cafe.miafetta.tsuki.widget

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.RemoteViews
import cafe.miafetta.tsuki.R

// One Endfield row: its cell, dot matrix, icon, label, current/slash/max values and loading placeholders.
internal class EndfieldRow(val metricLabel: String, val text: Int, val cell: Int, val dots: Int, val info: Int, val icon: Int,
    val label: Int, val value: Int, val slash: Int, val maximum: Int, val loadingLabel: Int, val loadingValue: Int)

// Four rows in official Android Skland order. Metric labels are the snapshot's; shown labels come from strings.xml.
internal val endfieldRows = listOf(
    EndfieldRow("活跃度", R.string.endfield_label_daily, R.id.endfield_daily_cell, R.id.endfield_daily_dots, R.id.endfield_daily_info,
        R.id.endfield_daily_icon, R.id.endfield_daily_label, R.id.endfield_daily, R.id.endfield_daily_slash, R.id.endfield_daily_max,
        R.id.endfield_loading_daily_label, R.id.endfield_loading_daily),
    EndfieldRow("每周事务", R.string.endfield_label_weekly, R.id.endfield_weekly_cell, R.id.endfield_weekly_dots, R.id.endfield_weekly_info,
        R.id.endfield_weekly_icon, R.id.endfield_weekly_label, R.id.endfield_weekly, R.id.endfield_weekly_slash, R.id.endfield_weekly_max,
        R.id.endfield_loading_weekly_label, R.id.endfield_loading_weekly),
    EndfieldRow("通行证", R.string.endfield_label_pass, R.id.endfield_pass_cell, R.id.endfield_pass_dots, R.id.endfield_pass_info,
        R.id.endfield_pass_icon, R.id.endfield_pass_label, R.id.endfield_pass, R.id.endfield_pass_slash, R.id.endfield_pass_max,
        R.id.endfield_loading_pass_label, R.id.endfield_loading_pass),
    EndfieldRow("蚀像寻遗", R.string.endfield_label_seek, R.id.endfield_seek_cell, R.id.endfield_seek_dots, R.id.endfield_seek_info,
        R.id.endfield_seek_icon, R.id.endfield_seek_label, R.id.endfield_seek, R.id.endfield_seek_slash, R.id.endfield_seek_max,
        R.id.endfield_loading_seek_label, R.id.endfield_loading_seek),
)

// Reference sizes of the card (360dp wide), the same as gen_endfield_layouts.py writes into the layouts.
private object EndfieldGeometry {
    const val BAND = 28f
    const val HEADER = 24.5f
    const val SIDE = 9.5f
    const val BOTTOM = 9.5f
    const val PANEL_GAP = 6f
    const val ROWS_SIDE = 9.5f
    const val ROWS_VERTICAL = 2.5f
    const val DIVIDER = 0.6f
    const val ICON = 15f
    const val ICON_GAP = 5.8f
    const val STRIPE = 16f
    const val BAND_MARKS_W = 5f
    const val BAND_MARKS_H = 31.3f
    const val BAND_MARKS_TOP = 5f
    const val BAND_MARKS_END = 4f
    const val BAND_WORD_W = 16f
    const val BAND_WORD_H = 78f
    const val BAND_WORD_BOTTOM = 9.5f
    const val BAND_WORD_END = 4f
    const val BRAND_WIDTH = 83f
    const val BRAND_HEIGHT = 12f
    const val PANEL_MARK_W = 120f
    const val PANEL_MARK_H = 96f
}

private val stripeIds = listOf(R.id.endfield_stripe_magenta, R.id.endfield_stripe_yellow, R.id.endfield_stripe_cyan)
private val dividerIds = listOf(R.id.endfield_divider_1, R.id.endfield_divider_2, R.id.endfield_divider_3)

// The frame both Endfield cards share: band, insets, header, panels, 4 rows and bottom 3-color stripe.
internal fun sizeEndfieldFrame(sizer: CardSizer) = with(EndfieldGeometry) {
    sizer.width(R.id.endfield_band, BAND)
    sizer.width(R.id.endfield_band_hatch, BAND)
    sizer.size(R.id.endfield_band_marks, BAND_MARKS_W, BAND_MARKS_H)
    sizer.margin(R.id.endfield_band_marks, RemoteViews.MARGIN_TOP, BAND_MARKS_TOP)
    sizer.margin(R.id.endfield_band_marks, RemoteViews.MARGIN_END, BAND_MARKS_END)
    sizer.size(R.id.endfield_band_word, BAND_WORD_W, BAND_WORD_H)
    sizer.margin(R.id.endfield_band_word, RemoteViews.MARGIN_BOTTOM, BAND_WORD_BOTTOM)
    sizer.margin(R.id.endfield_band_word, RemoteViews.MARGIN_END, BAND_WORD_END)
    sizer.views.setViewPadding(R.id.endfield_main, sizer.px(SIDE), 0, sizer.px(SIDE), sizer.px(BOTTOM))
    sizer.height(R.id.status_header, HEADER)
    sizer.size(R.id.endfield_brand_mark, BRAND_WIDTH, BRAND_HEIGHT)
    sizer.size(R.id.status_refresh_arrow, 11.5f)
    sizer.padding(R.id.status_refresh_hit, 8f, 0f)
    sizer.margin(R.id.status_sanity_primary, RemoteViews.MARGIN_END, PANEL_GAP)
    sizer.size(R.id.endfield_panel_mark, PANEL_MARK_W, PANEL_MARK_H)
    sizer.views.setViewPadding(R.id.endfield_rows, sizer.px(ROWS_SIDE), sizer.px(ROWS_VERTICAL), sizer.px(ROWS_SIDE),
        sizer.px(ROWS_VERTICAL))
    for (id in dividerIds) sizer.height(id, DIVIDER)
    for (row in endfieldRows) {
        sizer.size(row.dots, 38f, 19f)
        sizer.margin(row.dots, RemoteViews.MARGIN_START, 24f)
        sizer.size(row.icon, ICON)
        sizer.margin(row.icon, RemoteViews.MARGIN_END, ICON_GAP)
    }
    sizer.height(R.id.endfield_stripe, 2.5f)
    sizer.margin(R.id.endfield_stripe, RemoteViews.MARGIN_END, 24f)
    for (id in stripeIds) sizer.width(id, STRIPE)
}

internal fun sizeEndfieldContent(views: RemoteViews, context: Context, scale: Float) {
    val sizer = CardSizer(views, context, scale)
    // OEM hosts can allocate less than their reported options; weights must use the actual bounds.
    val matchParent = ViewGroup.LayoutParams.MATCH_PARENT.toFloat()
    views.setViewLayoutWidth(R.id.endfield_content, matchParent, TypedValue.COMPLEX_UNIT_PX)
    views.setViewLayoutHeight(R.id.endfield_content, matchParent, TypedValue.COMPLEX_UNIT_PX)
    sizeEndfieldFrame(sizer)
    sizer.margin(R.id.status_refresh, RemoteViews.MARGIN_START, 2.8f)
    sizer.size(R.id.status_sanity_icon, 53f, 56f)
    sizer.margin(R.id.status_sanity_icon, RemoteViews.MARGIN_TOP, 2f)
    views.setViewPadding(R.id.endfield_sanity_info, sizer.px(9.5f), sizer.px(9.5f), sizer.px(7f), sizer.px(9.5f))
    sizer.padding(R.id.endfield_sanity_slash, 2f)
    sizer.margin(R.id.endfield_sanity_label, RemoteViews.MARGIN_TOP, 1.5f)
    sizer.height(R.id.status_sanity_timer_row, 19f)
    sizer.margin(R.id.status_sanity_timer_row, RemoteViews.MARGIN_TOP, 3f)
    sizer.padding(R.id.status_sanity_timer_row, 7.5f, 9.5f)
    sizer.size(R.id.status_sanity_clock, 4f)
    sizer.margin(R.id.status_sanity_clock, RemoteViews.MARGIN_END, 4f)
    for (row in endfieldRows) {
        sizer.margin(row.label, RemoteViews.MARGIN_END, 4f)
        sizer.padding(row.value, 0f, 3f)
        sizer.width(row.slash, 6f)
        sizer.width(row.maximum, 27f)
    }
}

// The loading card at the placed card's sizes, so nothing moves when data replaces the bars.
internal fun sizeEndfieldLoading(views: RemoteViews, context: Context, width: Float, height: Float) {
    val sizer = CardSizer(views, context, referenceScale(width, height))
    sizeEndfieldFrame(sizer)
    val bars = listOf(R.id.endfield_loading_refresh to (22f to 8f), R.id.endfield_loading_sanity to (52f to 14f),
        R.id.endfield_loading_sanity_label to (26f to 9f), R.id.endfield_loading_recovery to (40f to 8f),
        R.id.endfield_loading_sanity_time to (46f to 7f)) +
        endfieldRows.flatMap { listOf(it.loadingLabel to (44f to 8f), it.loadingValue to (28f to 8f)) }
    for ((id, size) in bars) sizer.size(id, size.first, size.second)
    sizer.margin(R.id.endfield_loading_refresh, RemoteViews.MARGIN_START, 2.8f)
    sizer.margin(R.id.endfield_loading_sanity_label, RemoteViews.MARGIN_TOP, 4f)
}
