package cafe.miafetta.tsuki.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.providers.AvatarCache
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import java.io.File

// The default render run, on the main thread: renderer output in both palettes, every refresh and training state,
// three font scales and three host heights. A fixed magenta avatar makes avatar and portrait pixels identifiable.
internal class WidgetRenderCases(private val context: Context) {
    val provider: AppWidgetProviderInfo = AppWidgetManager.getInstance(context).installedProviders
        .single { it.provider.className == StatusWidgetProvider::class.java.name }
    private val config = WidgetInstanceConfig(1, "fake.skland", WidgetRuntime.FEATURE, "demo-1", "amiya")
    private val avatarUrl = "https://web.hycdn.cn/arknights/game/assets/char/avatar/char_test_trainee.png"
    private val png = java.io.ByteArrayOutputStream().apply {
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
            .compress(Bitmap.CompressFormat.PNG, 100, this)
    }.toByteArray()
    private val cache = AvatarCache(File(context.cacheDir, "render-test-avatars")) { png }
    private val renderer = StatusWidgetRenderer(cache)
    private val snapshot = FakeArknightsAdapter().fetch(config, 1700000000000).copy(
        avatarUrl = avatarUrl,
        training = WidgetTraining(TrainingState.TRAINING, "char_test_trainee", "测试受训干员", 2,
            System.currentTimeMillis() + 3600000, avatarUrl),
        metrics = FakeArknightsAdapter().fetch(config, 1700000000000).metrics.map {
            if (it.label == "理智" || it.label == "无人机") {
                it.copy(recoveryAt = System.currentTimeMillis() + 600000, estimated = true)
            } else if (canonicalMetricLabel(it.label) in listOf("订单进度", "制造进度")) it.copy(estimated = true) else it
        })
    private var cases = 0

    fun avatarCrop() {
        cache.prepare(avatarUrl)
        check(cache.read(avatarUrl) != null)
        // Official player avatars bake in a dark frame; the rounded crop must not show it as a rim.
        val framed = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
        for (y in 0 until 150) for (x in 0 until 150) {
            val edge = minOf(x, y, 149 - x, 149 - y)
            val value = if (edge >= 7) 200 else 90 + edge * 15
            framed.setPixel(x, y, Color.rgb(value, value, value))
        }
        val trimmed = roundedAvatar(framed, 1f / 5.4f)
        check(Color.red(trimmed.getPixel(trimmed.width / 2, 1)) >= 185) { "Avatar keeps its baked dark frame" }
        cases++
    }

    // Returns the number of cases including avatarCrop.
    fun run(): Int {
        for (light in listOf(false, true)) {
            trainingPortraits(light)
            trainingIconEdges(light)
            hostBlurSurface(light, surfaceRim(light))
        }
        for (scale in listOf(1f, 1.3f, 1.5f)) {
            val scaled = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { fontScale = scale })
            for (height in listOf(150, 162, 180)) hostHeightCases(scaled, scale, height)
        }
        endfieldHostCases()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val remote = executor.submit<android.widget.RemoteViews> {
                renderer.render(context, 1, WidgetRecord(config, snapshot, RefreshState.READY))
            }.get(10, java.util.concurrent.TimeUnit.SECONDS)
            remote.apply(context, android.widget.FrameLayout(context))
            cases++
        } finally { executor.shutdownNow() }
        return cases
    }

    private fun theme(light: Boolean) = if (light) WidgetTheme.LIGHT else WidgetTheme.DARK

    private fun trainingPortraits(light: Boolean) {
        for (trainingState in TrainingState.entries) {
            val occupied = snapshot.copy(training = snapshot.training.copy(state = trainingState, endsAt = 0))
            val record = WidgetRecord(config.copy(theme = theme(light)), occupied, RefreshState.ERROR)
            val root = renderer.renderAtSize(context, 1, record, false, 360f, 162f)
                .apply(context, android.widget.FrameLayout(context))
            val portrait = (root.findViewById<ImageView>(R.id.status_trainee).drawable as BitmapDrawable).bitmap
            val size = portrait.width
            check(portrait.getPixel(size / 2, size / 2) == Color.MAGENTA) { "Trainee was replaced after $trainingState" }
            check(portrait.getPixel(size / 2, 0) != Color.MAGENTA) { "Trainee frame missing" }
            check((0 until size * 38 / 100).any { x ->
                (size * 62 / 100 until size).any { y -> portrait.getPixel(x, y) != Color.MAGENTA }
            }) { "Training badge missing" }
            cases++
        }
    }

    private fun trainingIconEdges(light: Boolean) {
        val trainingIcon = trainingIdleIcon(context, light)
        for (offset in 0..3) {
            val edges = listOf(trainingIcon.getPixel(49, offset), trainingIcon.getPixel(49, 97 - offset),
                trainingIcon.getPixel(offset, 49), trainingIcon.getPixel(97 - offset, 49))
            if (offset < 2) check(edges.distinct().size == 1) { "Training frame has unequal edges" }
        }
    }

    // Draws the rim on a plain card surface; returns the palette's background color.
    private fun surfaceRim(light: Boolean): Int {
        val surface = Bitmap.createBitmap(1028, 462, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(surface)
        val color = context.getColor(if (light) R.color.widget_background_light else R.color.widget_background_dark)
        canvas.drawRoundRect(android.graphics.RectF(0f, 0f, 1028f, 462f), 67.848f, 67.848f,
            android.graphics.Paint().apply { this.color = color })
        val before = surface.getPixel(514, 2)
        drawSurfaceRim(canvas, light)
        check(surface.getPixel(514, 2) != before) { "Surface rim missing" }
        if (!light) check(Color.red(surface.getPixel(100, 2)) - Color.red(color) <= 25) {
            "Dark surface rim is too bright"
        }
        check(surface.getPixel(514, 231) == color) { "Surface rim changed the interior" }
        check(Color.alpha(surface.getPixel(0, 0)) == 0) { "Surface rim filled the rounded corner" }
        return color
    }

    private fun hostBlurSurface(light: Boolean, color: Int) {
        val record = WidgetRecord(config.copy(theme = theme(light)), snapshot, RefreshState.READY)
        for (blur in listOf(false, true, false)) {
            val hostView = renderer.renderWithSurface(context, 1, record, false, blur)
                .apply(context, android.widget.FrameLayout(context))
            val rendered = (hostView.findViewById<ImageView>(R.id.status_card_image).drawable as BitmapDrawable).bitmap
            check(Color.alpha(rendered.getPixel(514, 100)) == if (blur) materialTintAlpha(record.config.theme) else 255) {
                "Host blur / fallback surface did not switch correctly: light=$light blur=$blur " +
                    "alpha=${Color.alpha(rendered.getPixel(514, 100))}"
            }
            if (blur && !light) {
                val pixel = rendered.getPixel(514, 100)
                check(listOf(Color.red(pixel) - Color.red(color), Color.green(pixel) - Color.green(color),
                    Color.blue(pixel) - Color.blue(color)).all { kotlin.math.abs(it) <= 1 }) {
                    "Host material lost the instance theme tint"
                }
            }
        }
    }

    // One host per font scale and height: every palette switch and refresh state reuses it, as a launcher would.
    private fun hostHeightCases(context: Context, scale: Float, height: Int) {
        val density = context.resources.displayMetrics.density
        val host = AppWidgetHostView(context)
        host.setAppWidget(1, provider)
        host.setPadding(0, 0, 0, 0)
        host.updateAppWidget(android.widget.RemoteViews(context.packageName, themeLayout(WidgetTheme.DARK, false)))
        var previous = host.findViewById<View>(android.R.id.background)
        // Simulate the cached pre-upgrade hierarchy before checking replacement.
        val oldSummary = previous.findViewById<android.widget.GridLayout>(R.id.status_summary)
        for (index in 0 until oldSummary.childCount) {
            val cell = oldSummary.getChildAt(index)
            (cell.layoutParams as android.widget.GridLayout.LayoutParams).setGravity(android.view.Gravity.FILL_HORIZONTAL)
        }
        var previousTheme: WidgetTheme? = null
        for (theme in listOf(WidgetTheme.DARK, WidgetTheme.LIGHT, WidgetTheme.LIGHT, WidgetTheme.DARK)) {
            for (state in RefreshState.entries) {
                val record = WidgetRecord(config.copy(theme = theme), snapshot, state)
                val remote = renderer.renderAtSize(context, 1, record, false, 360f, height.toFloat())
                // apply() exposes the actual action/inflation exception that the host otherwise hides.
                remote.apply(context, host)
                host.updateAppWidget(remote)
                measureHost(host, density, height)
                val root = requireNotNull(host.findViewById<View>(android.R.id.background))
                if (previousTheme != theme) check(root !== previous) { "Theme reused the previous root" }
                checkScaledRoot(host, root, height)
                checkSurfaceAndAvatar(context, root, theme)
                checkThemeColors(context, root, theme)
                checkProgressLines(root, snapshot)
                checkSummaryRows(root, "density=$density fontScale=$scale theme=$theme hostHeight=$height state=$state")
                checkValueTexts(root, snapshot, scale, height)
                checkSummaryLabels(root)
                checkCardTexts(root, snapshot, record)
                previous = root
                previousTheme = theme
                cases++
                checkImageHost(context, renderer, provider, record, height)
                cases++
            }
        }
        for (record in listOf<WidgetRecord?>(null, WidgetRecord(config.copy(theme = WidgetTheme.LIGHT),
            snapshot.copy(metrics = snapshot.metrics.take(2), training = WidgetTraining()), RefreshState.ERROR))) {
            host.updateAppWidget(renderer.renderAtSize(context, 1, record, false, 360f, height.toFloat()))
            check(host.findViewById<TextView>(R.id.status_training).text == "未知")
            cases++
        }
    }

    private fun endfieldHostCases() {
        val endfieldProvider = AppWidgetManager.getInstance(context).installedProviders
            .single { it.provider.className == EndfieldWidgetProvider::class.java.name }
        val seekIds = listOf(R.id.endfield_seek_label, R.id.endfield_seek, R.id.endfield_seek_slash, R.id.endfield_seek_max)
        for (scale in listOf(1f, 1.3f, 1.5f)) {
            val scaled = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { fontScale = scale })
            val density = scaled.resources.displayMetrics.density
            val host = AppWidgetHostView(scaled).apply {
                setAppWidget(1, endfieldProvider)
                setPadding(0, 0, 0, 0)
            }
            for (height in listOf(150, 162, 180)) for (seekValue in listOf(0, 200_000)) {
                val sample = WidgetSnapshot("终末地博士", listOf(
                    WidgetMetric("理智", 125, 125), WidgetMetric("活跃度", 60, 100),
                    WidgetMetric("每周事务", 4, 10), WidgetMetric("通行证", 12, 60),
                    WidgetMetric("蚀像寻遗", seekValue, 200_000)), System.currentTimeMillis())
                val remote = EndfieldCard.render(scaled, 1, WidgetRecord(config, sample, RefreshState.READY), false,
                    360f, height.toFloat())
                remote.apply(scaled, host)
                host.updateAppWidget(remote)
                measureHost(host, density, height)
                val info = requireNotNull(host.findViewById<android.view.ViewGroup>(R.id.endfield_seek_info))
                check(host.findViewById<TextView>(R.id.endfield_seek).text.toString() == seekValue.toString())
                check(host.findViewById<TextView>(R.id.endfield_seek_max).text.toString() == "200000")
                for (id in seekIds) {
                    val tv = host.findViewById<TextView>(id)
                    check(!tv.text.contains("w")) { "Unexpected w suffix in $id: ${tv.text}" }
                    check(tv.width > 0 && tv.height > 0 && tv.layout.lineCount == 1) { "Bad seek layout: $id" }
                    check(tv.layout.getEllipsisCount(0) == 0 && tv.layout.getLineEnd(0) == tv.text.length) { "Clipped seek $id" }
                    check(tv.layout.getLineWidth(0) <= tv.width - tv.totalPaddingLeft - tv.totalPaddingRight + 1) {
                        "Overflow seek $id"
                    }
                    val box = android.graphics.Rect(0, 0, tv.width, tv.height)
                    info.offsetDescendantRectToMyCoords(tv, box)
                    check(box.left >= 0 && box.right <= info.width) { "Seek view $id outside row: $box vs ${info.width}" }
                }
                cases++
            }
        }
    }
}

// Lays the host out at 360 dp wide and the given height, as a 4x2 launcher cell would.
internal fun measureHost(host: AppWidgetHostView, density: Float, height: Int) {
    host.measure(View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
    host.layout(0, 0, host.measuredWidth, host.measuredHeight)
}
