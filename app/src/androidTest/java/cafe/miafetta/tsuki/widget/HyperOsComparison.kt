package cafe.miafetta.tsuki.widget

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.providers.AvatarCache
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import java.io.File

internal fun hyperOsComparison(runner: Instrumentation): String {
    val comparison = HyperOsComparison(runner.targetContext)
    var failure: Throwable? = null
    runner.runOnMainSync { try { comparison.run() } catch (error: Throwable) { failure = error } }
    failure?.let { throw it }
    return "${comparison.cases} comparison checks passed\n${comparison.report}"
}

// Widget sizes in dp, from the smallest HyperOS cell to an enlarged 4x2.
internal val comparisonSizes = listOf(210 to 94, 280 to 110, 300 to 140, 336 to 152, 360 to 162, 400 to 180, 600 to 250,
    800 to 360)

// Native RemoteViews checked the way HyperOS hosts them: actions built in day mode and inflated in either mode, reapplied
// over stale views, and laid out at every cell size and font scale. The remaining steps are in HyperOsComparisonHost.kt.
internal class HyperOsComparison(val context: Context) {
    val renderer = StatusWidgetRenderer(AvatarCache(File(context.cacheDir, "comparison-avatars")))
    val config = WidgetInstanceConfig(1, "fake.skland", WidgetRuntime.FEATURE, "demo-1", "amiya")
    val day: Context = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        uiMode = Configuration.UI_MODE_NIGHT_NO
    })
    val snapshot = FakeArknightsAdapter().fetch(config, System.currentTimeMillis())
    val record = WidgetRecord(config, snapshot, RefreshState.READY)
    private val folder = File(context.getExternalFilesDir(null), "native-tests").apply { mkdirs() }
    var cases = 0
    val report = StringBuilder()

    fun run() {
        for ((uri, expected) in listOf("tsuki://configure/4242?miuiWidgetId=4242" to 4242,
            "tsuki://configure/4242?miuiWidgetId=4241" to -1, "https://configure/4242" to -1,
            "tsuki://configure/0" to -1, "tsuki://configure/2147483648" to -1)) {
            check(cafe.miafetta.tsuki.widgetConfigId(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))) == expected)
            cases++
        }
        for (theme in WidgetTheme.entries) for (night in listOf(false, true)) themedCases(theme, night)
        cachedModeSwitch()
        staleReapply()
        loadingCards()
        check(renderer.render(context, 0, record).apply(context, FrameLayout(context))
            .findViewById<View>(R.id.status_card_image) == null) { "Default renderer still publishes a full-card bitmap" }
        cases++
        referencePreviews()
        parcelTimings()
        fontSizes()
        smallSizes()
    }

    private fun themedCases(theme: WidgetTheme, night: Boolean) {
        val themed = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
        val record = WidgetRecord(config.copy(theme = theme), snapshot, RefreshState.READY)
        val surface = themed.getDrawable(if (theme == WidgetTheme.LIGHT)
            R.drawable.widget_background_light else R.drawable.widget_background)!!.mutate()
        val sample = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        surface.setBounds(0, 0, 32, 32)
        surface.draw(Canvas(sample))
        check(android.graphics.Color.alpha(sample.getPixel(16, 16)) == materialTintAlpha(theme))
        sample.recycle()
        cases++
        for ((width, height) in comparisonSizes) nativeSize(themed, record, night, width, height)
        for (state in RefreshState.entries) {
            val views = renderer.renderAtSize(themed, 0, record.copy(state = state), false, 336f, 152f, native = true)
                .apply(themed, FrameLayout(themed))
            val description = views.findViewById<View>(R.id.status_refresh_hit).contentDescription
            check(description.contains(cardStatus(record.copy(state = state))))
            cases++
        }
        progressReapply(themed, record, night)
    }

    private fun nativeSize(themed: Context, record: WidgetRecord, night: Boolean, width: Int, height: Int) {
        val theme = record.config.theme
        // Reuse actions created in day mode while the host inflates them in either mode.
        val remote = renderer.renderAtSize(day, 0, record, false, width.toFloat(), height.toFloat(), native = true)
        val root = remote.apply(themed, FrameLayout(themed))
        check(!root.isForceDarkAllowed)
        val density = themed.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        for ((id, label) in listOf(R.id.status_jade_progress to "合成玉", R.id.status_tower_progress to "保全酬劳")) {
            val bar = root.findViewById<android.widget.LinearLayout>(id)
            val level = progressLevel(cardMetric(snapshot, label))
            check(bar.visibility == if (level == null) View.INVISIBLE else View.VISIBLE)
            if ((level ?: 0) > 0) check(kotlin.math.abs(bar.getChildAt(0).width - bar.width * level!! / 10000f) <= 1f)
        }
        check(root.findViewById<View>(R.id.status_card_image) == null)
        check(root.findViewById<View>(android.R.id.background).contentDescription == null)
        checkNativeFields(root, "${width}x$height")
        check(root.findViewById<TextView>(R.id.status_sanity).contentDescription.startsWith("理智"))
        check(root.findViewById<TextView>(R.id.status_title).currentTextColor == themed.getColor(
            if (theme == WidgetTheme.LIGHT) R.color.widget_text_light else R.color.widget_text_dark))
        if (theme == WidgetTheme.LIGHT && night) {
            check(root.findViewById<TextView>(R.id.status_title).currentTextColor == themed.getColor(R.color.widget_text_dark))
            check(root.findViewById<TextView>(R.id.status_sanity_time).currentTextColor ==
                themed.getColor(R.color.widget_secondary_dark))
        }
        val bitmap = hostTint(themed, root, theme, night)
        File(folder, "${theme.name}-${night}-${width}x$height.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        cases++
    }

    // Draws the card over the host's background and checks the palette's tint survives; returns the drawn card.
    private fun hostTint(themed: Context, root: View, theme: WidgetTheme, night: Boolean): Bitmap {
        val density = themed.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        // Light uses host glass; dark keeps the original test widget's opaque root.
        if (theme == WidgetTheme.LIGHT) root.setBackgroundColor(
            if (night) themed.getColor(R.color.widget_background_dark) else android.graphics.Color.WHITE)
        root.findViewById<View>(R.id.status_native_content).background = null
        root.draw(Canvas(bitmap))
        val tint: android.widget.ImageView? = root.findViewById(R.id.status_native_surface)
        if (theme == WidgetTheme.DARK) {
            check(root is android.widget.LinearLayout && tint == null)
            check(root.background is android.graphics.drawable.GradientDrawable)
        } else {
            check(tint?.visibility == View.GONE)
            check(tint.drawable is android.graphics.drawable.GradientDrawable)
        }
        val bottom = bitmap.getPixel(root.width / 2, root.height - (2 * density).toInt())
        val base = themed.getColor(if (theme == WidgetTheme.LIGHT) R.color.widget_background_light
            else R.color.widget_background_dark)
        val alpha = if (theme == WidgetTheme.LIGHT) 0 else 255
        val hostRed = if (night) android.graphics.Color.red(themed.getColor(R.color.widget_background_dark)) else 255
        val expectedRed = (android.graphics.Color.red(base) * alpha + hostRed * (255 - alpha)) / 255
        check(kotlin.math.abs(android.graphics.Color.red(bottom) - expectedRed) <= 1) {
            "Lost $theme tint over host background: $bottom"
        }
        return bitmap
    }

    // Reapplying new progress over a laid-out card, including one a host flipped to RTL, must redraw both bars.
    private fun progressReapply(themed: Context, record: WidgetRecord, night: Boolean) {
        val theme = record.config.theme
        val root = renderer.renderAtSize(themed, 0, record, false, 360f, 162f, native = true)
            .apply(themed, FrameLayout(themed))
        val density = themed.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((162 * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        for ((jade, tower) in listOf(0 to 0, 370 to 0, 900 to 42, 1800 to 84, 0 to 0)) {
            val changed = snapshot.copy(metrics = snapshot.metrics.map { metric ->
                when (canonicalMetricLabel(metric.label)) {
                    "合成玉" -> metric.copy(value = jade, maximum = 1800)
                    "保全酬劳" -> metric.copy(value = tower, maximum = 84)
                    else -> metric
                }
            })
            for (id in listOf(R.id.status_jade_progress, R.id.status_tower_progress))
                root.findViewById<View>(id).layoutDirection = View.LAYOUT_DIRECTION_RTL
            renderer.renderAtSize(day, 0, record.copy(snapshot = changed), false, 360f, 162f, native = true)
                .reapply(themed, root)
            root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            for (id in listOf(R.id.status_jade_progress, R.id.status_tower_progress)) {
                val bar = root.findViewById<android.widget.LinearLayout>(id)
                val fill = bar.getChildAt(0)
                val label = if (id == R.id.status_jade_progress) "合成玉" else "保全酬劳"
                val level = checkNotNull(progressLevel(cardMetric(changed, label)))
                check(bar.layoutDirection == View.LAYOUT_DIRECTION_LTR)
                check(fill.visibility == if (level > 0) View.VISIBLE else View.GONE)
                if (level > 0) check(kotlin.math.abs(fill.width - bar.width * level / 10000f) <= 1f)
                val pixels = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
                bar.draw(Canvas(pixels))
                for ((position, filled) in listOf(.1f to (level > 1000), .9f to (level == 10000))) {
                    val color = pixels.getPixel((bar.width * position).toInt(), bar.height / 2)
                    val expected = themed.getColor(if (filled) {
                        if (theme == WidgetTheme.LIGHT) R.color.widget_progress_light else R.color.widget_progress_dark
                    } else if (theme == WidgetTheme.LIGHT) R.color.widget_track_light else R.color.widget_track_dark)
                    check(color == expected) { "Progress pixels differ: $theme night=$night jade=$jade tower=$tower" }
                }
                pixels.recycle()
                cases++
            }
        }
        val unknown = snapshot.copy(metrics = snapshot.metrics.filter {
            canonicalMetricLabel(it.label) !in listOf("合成玉", "保全酬劳")
        })
        renderer.renderAtSize(themed, 0, record.copy(snapshot = unknown), false, 360f, 162f, native = true)
            .reapply(themed, root)
        for (id in listOf(R.id.status_jade_progress, R.id.status_tower_progress)) {
            val bar = root.findViewById<android.widget.LinearLayout>(id)
            check(bar.visibility == View.INVISIBLE && bar.getChildAt(0).visibility == View.GONE)
            cases++
        }
    }
}

// Every native field is visible, untruncated, unclipped and inside the card; `size` names the cell for messages.
private fun checkNativeFields(root: View, size: String) {
    for (id in listOf(R.id.status_title, R.id.status_sanity, R.id.status_sanity_time,
        R.id.status_drone, R.id.status_drone_time, R.id.status_native_training_name, R.id.status_native_training_time,
        R.id.status_recruit, R.id.status_fatigue, R.id.status_orders, R.id.status_jade,
        R.id.status_daily, R.id.status_rest, R.id.status_manufacture, R.id.status_tower,
        R.id.status_data_time, R.id.status_refresh)) {
        val text = root.findViewById<TextView>(id)
        check(text.width > 0 && text.height > 0 && text.text.isNotBlank()) { "Hidden native field $id at $size" }
        check(text.layout != null && text.layout.getEllipsisCount(0) == 0) {
            "Truncated native field ${text.text} at $size"
        }
        check(text.layout.getLineWidth(0) <= text.width + 1f) { "Clipped native field ${text.text} at $size" }
        val ink = android.graphics.Rect()
        text.paint.getTextBounds(text.text.toString(), 0, text.text.length, ink)
        check(text.baseline + ink.top >= -1 && text.baseline + ink.bottom <= text.height + 1) {
            "Native glyphs cropped vertically: ${text.text}, baseline=${text.baseline}, ink=$ink, height=${text.height}"
        }
        val bounds = android.graphics.RectF(0f, 0f, text.width.toFloat(), text.height.toFloat())
        val matrix = android.graphics.Matrix()
        text.transformMatrixToGlobal(matrix)
        val rootMatrix = android.graphics.Matrix()
        root.transformMatrixToGlobal(rootMatrix)
        check(rootMatrix.invert(rootMatrix))
        matrix.postConcat(rootMatrix)
        matrix.mapRect(bounds)
        check(bounds.top >= -1f && bounds.left >= -1f && bounds.bottom <= root.height + 1f && bounds.right <= root.width + 1f) {
            "Field $id outside $size native card: ${text.text}, $bounds / ${root.width}x${root.height}"
        }
    }
}
