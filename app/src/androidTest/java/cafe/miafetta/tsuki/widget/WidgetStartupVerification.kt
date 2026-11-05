package cafe.miafetta.tsuki.widget

import android.app.Instrumentation
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Layout
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.core.WidgetTheme
import cafe.miafetta.tsuki.providers.AvatarCache
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import java.io.File
import kotlin.math.abs

// Cards a host shows before and during the first fetch: the cold initial layout, the picker preview and the runtime
// loading card. None of them may be a blank surface or a card of unknown values. Both loading cards must fit every cell
// size, the cold one by its XML alone; the runtime one must also keep the frame of the native card that replaces it.
internal fun widgetStartupVerification(runner: Instrumentation): Pair<Boolean, String> {
    var failure: Throwable? = null
    var cases = 0
    runner.runOnMainSync {
        try {
            val context = runner.targetContext
            val providers = AppWidgetManager.getInstance(context).installedProviders.filter {
                it.provider.className in setOf(StatusWidgetProvider::class.java.name, OpaqueWidgetProvider::class.java.name)
            }
            check(providers.size == 2)
            val renderer = StatusWidgetRenderer(AvatarCache(File(context.cacheDir, "official-avatars")))
            for (provider in providers) cases += startupCases(context, renderer, provider)
            RemoteViews(context.packageName, R.layout.status_widget).apply(context, FrameLayout(context))
            cases++
        } catch (error: Throwable) { failure = error }
    }
    return if (failure == null) true to "PASS $cases cold widget layout cases" else false to requireNotNull(failure).stackTraceToString()
}

// Returns the number of cases checked for one provider.
private fun startupCases(context: Context, renderer: StatusWidgetRenderer, provider: AppWidgetProviderInfo): Int {
    val light = provider.provider.className == StatusWidgetProvider::class.java.name
    val theme = if (light) WidgetTheme.LIGHT else WidgetTheme.DARK
    val host = AppWidgetHostView(context)
    host.setAppWidget(-1, provider)
    host.updateAppWidget(null)
    val cold = checkNotNull(host.findViewById<View>(android.R.id.background)) {
        "Cold initial layout failed: ${provider.provider.className}"
    }
    for ((width, height) in comparisonSizes) checkLoadingCard(context, cold, width, height, "cold $theme")
    check(provider.previewLayout == if (light) R.layout.status_widget_preview_light else R.layout.status_widget_preview_dark)
    checkPreviewCard(context, RemoteViews(context.packageName, provider.previewLayout).apply(context, FrameLayout(context)), theme)
    val config = WidgetInstanceConfig(1, "fake.skland", WidgetRuntime.FEATURE, "demo-1", "amiya", theme)
    val first = WidgetRecord(config, null, RefreshState.LOADING)
    val snapshot = FakeArknightsAdapter().fetch(config, System.currentTimeMillis())
    for ((width, height) in comparisonSizes) {
        val options = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width.toFloat(), height.toFloat())))
        }
        val loading = renderer.renderNative(context, 1, first, false, options).apply(context, FrameLayout(context))
        checkLoadingCard(context, loading, width, height, "first fetch $theme")
        val ready = first.copy(snapshot = snapshot, state = RefreshState.READY)
        val native = renderer.renderNative(context, 1, ready, false, options).apply(context, FrameLayout(context))
        layOut(context, native, width, height)
        checkSameFrame(loading, native, "$theme ${width}x$height")
    }
    val loading = renderer.renderNative(context, 1, first, false, Bundle()).apply(context, FrameLayout(context))
    check(loading.hasOnClickListeners() && loading.findViewById<View>(R.id.status_refresh_hit).hasOnClickListeners()) {
        "Loading card lost its actions: $theme"
    }
    // A cached snapshot, a failure, an unconfigured card and a storage error keep the full card and its status text.
    for ((record, storageError) in listOf(first.copy(snapshot = snapshot) to false, first.copy(state = RefreshState.ERROR) to false,
        null to false, first to true)) {
        val card = renderer.renderNative(context, 1, record, storageError, Bundle()).apply(context, FrameLayout(context))
        check(card.findViewById<View>(R.id.status_native_content).visibility == View.VISIBLE) {
            "Full card replaced: $theme state=${record?.state} storageError=$storageError"
        }
    }
    return 2 * comparisonSizes.size + 3
}

private fun descendants(view: View): List<View> =
    listOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { descendants(group.getChildAt(it)) } }
        ?: emptyList())

private fun name(view: View) = if (view.id == View.NO_ID) view.javaClass.simpleName else view.resources.getResourceEntryName(view.id)

// Lays a card out at a cell size in dp, by default the 360x162dp reference cell the static layouts are drawn for.
private fun layOut(context: Context, root: View, width: Int = 360, height: Int = 162) {
    val density = context.resources.displayMetrics.density
    root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
}

// Every shape is visible, has a size, stays inside its own parent and does not overlap a sibling.
private fun checkLoadingCard(context: Context, root: View, width: Int, height: Int, case: String) {
    val size = "$case ${width}x$height"
    check(root.id == android.R.id.background && root.visibility == View.VISIBLE && root.background != null) {
        "Loading card has no surface: $size"
    }
    check(root.contentDescription?.toString() == context.getString(R.string.status_loading)) { "Loading card undescribed: $size" }
    val views = descendants(root)
    check(views.none { it is TextView }) { "Loading card shows text: $size" }
    check(views.none { it.visibility != View.VISIBLE }) { "Loading card hides a shape: $size" }
    check(root.findViewById<View>(R.id.status_refresh_hit) != null) { "Loading card has no refresh area: $size" }
    layOut(context, root, width, height)
    for (group in views.filterIsInstance<ViewGroup>()) {
        val children = (0 until group.childCount).map(group::getChildAt)
        val bounds = children.map { Rect(it.left, it.top, it.right, it.bottom) }
        for ((index, child) in children.withIndex()) {
            check(child.width > 0 && child.height > 0 && bounds[index].left >= 0 && bounds[index].top >= 0 &&
                bounds[index].right <= group.width && bounds[index].bottom <= group.height) {
                "Loading shape ${name(child)} outside ${name(group)} at $size: ${bounds[index]} in ${group.width}x${group.height}"
            }
            for (other in index + 1 until children.size) check(!Rect.intersects(bounds[index], bounds[other])) {
                "Loading shapes ${name(child)} ${bounds[index]} and ${name(children[other])} ${bounds[other]} overlap at $size"
            }
        }
    }
}

// Frame regions the runtime loading card shares with the native card of the same size.
private val sharedFrameIds = listOf(R.id.status_header, R.id.status_avatar, R.id.status_refresh_arrow, R.id.status_primary,
    R.id.status_sanity_primary, R.id.status_drone_primary, R.id.status_training_primary, R.id.status_sanity_icon,
    R.id.status_drone_icon, R.id.status_summary, R.id.status_summary_row_first, R.id.status_summary_row_second,
    R.id.status_jade_cell, R.id.status_tower_cell) + summaryCellIds + summaryExtraCellIds

// Data replacing the placeholders must not move the frame: header, primary columns and summary cells stay within 2px.
private fun checkSameFrame(loading: View, native: View, case: String) {
    check(loading.width == native.width && loading.height == native.height) { "Cards laid out at different sizes: $case" }
    val pairs = sharedFrameIds.map { it to it } + (R.id.status_loading_trainee to R.id.status_native_training_avatar)
    for ((loadingId, nativeId) in pairs) {
        val actual = boundsIn(loading, loadingId)
        val expected = boundsIn(native, nativeId)
        val error = maxOf(abs(actual.left - expected.left), abs(actual.top - expected.top),
            abs(actual.right - expected.right), abs(actual.bottom - expected.bottom))
        check(error <= 2) {
            "Loading ${loading.resources.getResourceEntryName(loadingId)} is ${error}px off the native card at $case: $actual / $expected"
        }
    }
}

private fun boundsIn(root: View, id: Int): Rect {
    val view = checkNotNull(root.findViewById<View>(id)) { "Missing ${root.resources.getResourceEntryName(id)}" }
    return Rect(0, 0, view.width, view.height).also { (root as ViewGroup).offsetDescendantRectToMyCoords(view, it) }
}

private fun checkPreviewCard(context: Context, root: View, theme: WidgetTheme) {
    check(root.id == android.R.id.background && root.background != null) { "Preview has no surface: $theme" }
    layOut(context, root)
    check(descendants(root).all { it.visibility == View.VISIBLE }) { "Preview hides content: $theme" }
    val texts = descendants(root).filterIsInstance<TextView>()
    check(texts.size == 25) { "Preview lost sample fields: $theme" }
    for (text in texts) {
        check(text.width > 0 && text.text.isNotBlank() && "未知" !in text.text) { "Preview field ${text.text}: $theme" }
        check(fitsOneLine(text)) { "Preview field truncated: ${text.text} $theme" }
    }
    val labels = summaryLabelTexts.map { context.getString(it) }
    check(texts.map { it.text.toString() }.filter { it in labels } == labels) { "Preview summary labels differ: $theme" }
}

// Measured rather than read from ellipsis counts, which only fields with ellipsize report: the whole text sits on the
// first line and its natural width fits the field.
private fun fitsOneLine(text: TextView): Boolean {
    val layout = text.layout ?: return false
    val available = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
    return layout.getLineEnd(0) == text.text.length && layout.getEllipsisCount(0) == 0 &&
        Layout.getDesiredWidth(text.text, text.paint) <= available + 1f
}
