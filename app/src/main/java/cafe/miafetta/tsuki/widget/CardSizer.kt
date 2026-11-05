package cafe.miafetta.tsuki.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import kotlin.math.round

// Layout actions for one widget size, from dimensions given at the 360dp reference card. The native card and the loading
// card both size their shared frame through this class, so the placeholders sit where the data later appears.
internal class CardSizer(val views: RemoteViews, context: Context, val scale: Float) {
    val density = context.resources.displayMetrics.density

    fun px(dp: Float) = round(dp * scale * density).toInt()

    fun width(id: Int, dp: Float) = views.setViewLayoutWidth(id, dp * scale, UNITS)

    fun height(id: Int, dp: Float) = views.setViewLayoutHeight(id, dp * scale, UNITS)

    fun size(id: Int, width: Float, height: Float = width) {
        width(id, width)
        height(id, height)
    }

    fun margin(id: Int, side: Int, dp: Float) = views.setViewLayoutMargin(id, side, dp * scale, UNITS)

    fun padding(id: Int, start: Float, end: Float = start) = views.setViewPadding(id, px(start), 0, px(end), 0)

    // The pixels a scaled dp layout action resolves to: TypedValue rounds sizes and truncates margins.
    fun sizePx(dp: Float) = (dp * scale * density + .5f).toInt()

    fun offsetPx(dp: Float) = (dp * scale * density).toInt()

    companion object {
        private const val UNITS = TypedValue.COMPLEX_UNIT_DIP

        // Reference dimensions that both cards and the loading layout's weights use.
        const val INSET = 10.8f
        const val HEADER = 19.44f
        const val PRIMARY_GAP = 5.76f
        const val SUMMARY = 46.08f
        const val TITLE_GAP = 4.32f
        const val DATA_TIME_WIDTH = 40f
        const val VALUE_GAP = 2.34f
        const val ROW = 20.88f
        const val INFO = 17.28f
        const val PROGRESS = 1.26f
        const val PROGRESS_GAP = 1.98f

        // What the primary row gets at the reference height: everything the inset, header and summary leave.
        const val PRIMARY = REFERENCE_HEIGHT - 2 * INSET - HEADER - 2 * PRIMARY_GAP - SUMMARY
    }
}
