package cafe.miafetta.tsuki.widget

import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Parcel
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.*

// Steps of HyperOsComparison.run that depend on host behaviour: cached actions, stale views, previews, timing and options.

// Actions cached in day mode follow the host's mode when it inflates them later.
internal fun HyperOsComparison.cachedModeSwitch() {
    val idle = record.copy(config = config.copy(theme = WidgetTheme.LIGHT),
        snapshot = snapshot.copy(training = WidgetTraining(TrainingState.IDLE)))
    val cached = renderer.renderAtSize(day, 0, idle, false, 360f, 162f, native = true)
    for (night in listOf(false, true, false)) {
        val host = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
        val root = cached.apply(host, FrameLayout(host))
        check(root.findViewById<TextView>(R.id.status_title).currentTextColor == host.getColor(
            if (night) R.color.widget_text_dark else R.color.widget_text_light))
        for ((id, expected) in listOf(R.id.status_sanity_icon to R.drawable.widget_sanity_dark,
            R.id.status_drone_icon to R.drawable.widget_drone_dark)) {
            if (night) {
                val icon = root.findViewById<android.widget.ImageView>(id).drawable
                val pixels = Bitmap.createBitmap(98, 98, Bitmap.Config.ARGB_8888)
                val reference = Bitmap.createBitmap(98, 98, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 98, 98)
                icon.draw(Canvas(pixels))
                host.getDrawable(expected)!!.apply {
                    setBounds(0, 0, 98, 98)
                    draw(Canvas(reference))
                }
                check(pixels.sameAs(reference)) { "Cached light icon did not switch to dark mode" }
                pixels.recycle()
                reference.recycle()
            }
        }
        val training = (root.findViewById<android.widget.ImageView>(R.id.status_native_training_avatar).drawable
            as android.graphics.drawable.BitmapDrawable).bitmap
        val expected = trainingIdleIcon(host, !night)
        check(training.sameAs(expected)) { "Cached training frame did not follow the host mode" }
        expected.recycle()
        cases++
    }
}

// Reapplying over views a host left with stale drawables and backgrounds restores the clean card.
internal fun HyperOsComparison.staleReapply() {
    for (theme in WidgetTheme.entries) {
        val occupied = snapshot.copy(training = WidgetTraining(TrainingState.COMPLETE, "char_test_trainee", "克莱门莎"))
        val remote = renderer.renderAtSize(context, 0, record.copy(config = config.copy(theme = theme), snapshot = occupied),
            false, 336f, 152f, native = true)
        val root = remote.apply(context, FrameLayout(context))
        val name = root.findViewById<TextView>(R.id.status_native_training_name)
        val time = root.findViewById<TextView>(R.id.status_native_training_time)
        val staleImage = android.graphics.drawable.ColorDrawable(android.graphics.Color.MAGENTA).apply { setBounds(0, 0, 100, 100) }
        name.setCompoundDrawables(staleImage, null, null, null)
        time.background = staleImage
        val tint: android.widget.ImageView? = root.findViewById(R.id.status_native_surface)
        if (theme == WidgetTheme.LIGHT) {
            checkNotNull(tint).setImageDrawable(staleImage)
            tint.visibility = View.VISIBLE
        }
        root.findViewById<View>(R.id.status_native_content).setBackgroundColor(android.graphics.Color.GREEN)
        remote.reapply(context, root)
        check(name.text.toString() == "克莱门莎" && time.text.toString() == "已完成")
        check(name.compoundDrawables.all { it == null } && time.compoundDrawables.all { it == null })
        check(name.background == null && time.background == null)
        check(root.findViewById<android.widget.ImageView>(R.id.status_native_training_avatar) != null)
        check(root.findViewById<View>(R.id.status_trainee) == null)
        check(root.findViewById<View>(R.id.status_native_content).background == null)
        check(if (theme == WidgetTheme.LIGHT) tint?.visibility == View.GONE else tint == null)
        val restored = (if (theme == WidgetTheme.LIGHT) checkNotNull(tint).drawable else root.background)
            .constantState!!.newDrawable().mutate()
        val pixel = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        restored.setBounds(0, 0, 32, 32)
        restored.draw(Canvas(pixel))
        val alpha = if (theme == WidgetTheme.DARK) 255 else 0
        check(android.graphics.Color.alpha(pixel.getPixel(16, 16)) == alpha)
        val expectedColor = context.getColor(if (theme == WidgetTheme.LIGHT)
            R.color.widget_background_light else R.color.widget_background_dark)
        if (alpha > 0) for (shift in listOf(0, 8, 16)) check(kotlin.math.abs(
            ((pixel.getPixel(16, 16) shr shift) and 255) - ((expectedColor shr shift) and 255)) <= 1) {
            "Restored $theme surface lost its tint: ${pixel.getPixel(16, 16).toUInt().toString(16)}"
        }
        pixel.recycle()
        cases++
    }
}

// A first fetch without data gets the static loading card instead of the native one. Actions built in day mode are
// inflated in either mode, and a light card's placeholders must switch with the host as its whole palette does.
internal fun HyperOsComparison.loadingCards() {
    for (theme in WidgetTheme.entries) for (night in listOf(false, true)) {
        val host = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
        val first = WidgetRecord(config.copy(theme = theme), null, RefreshState.LOADING)
        val root = renderer.renderNative(day, 0, first, false, Bundle()).apply(host, FrameLayout(host))
        check(root.findViewById<View>(R.id.status_native_content) == null && root.findViewById<View>(R.id.status_title) == null) {
            "First fetch still renders the native card: $theme night=$night"
        }
        check(root.contentDescription?.toString() == host.getString(R.string.status_loading))
        val shape = root.findViewById<android.widget.ImageView>(R.id.status_avatar).drawable as android.graphics.drawable.GradientDrawable
        val expected = if (theme == WidgetTheme.LIGHT && !night) day.getColor(R.color.widget_placeholder_light)
            else context.getColor(R.color.widget_placeholder_dark)
        check(shape.color?.defaultColor == expected) { "Loading placeholder ignores the host mode: $theme night=$night" }
        cases++
    }
}

// The app's preview bitmap must match the native layout drawn at the reference size and density.
internal fun HyperOsComparison.referencePreviews() {
    val referenceContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        densityDpi = 457
        fontScale = 1f
    })
    for (theme in WidgetTheme.entries) {
        val root = renderer.renderAtSize(referenceContext, 0, record.copy(config = config.copy(theme = theme)), false,
            REFERENCE_WIDTH, REFERENCE_HEIGHT, native = true).apply(referenceContext, FrameLayout(referenceContext))
        root.measure(View.MeasureSpec.makeMeasureSpec(1028, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(462, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1028, 462)
        val expected = Bitmap.createBitmap(1028, 462, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(expected))
        val preview = renderer.preview(context, snapshot, theme)
        try { check(preview.sameAs(expected)) { "App $theme preview differs from the native widget layout" } }
        finally {
            preview.recycle()
            expected.recycle()
        }
        cases++
    }
}

internal fun HyperOsComparison.parcelTimings() {
    for (native in listOf(false, true)) {
        val times = mutableListOf<Long>()
        var parcelSize = 0
        repeat(12) {
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val remote = if (native) renderer.renderAtSize(context, 0, record, false, 336f, 152f, native = true)
                else renderer.renderWithSurface(context, 0, record, false, false)
            times += android.os.SystemClock.elapsedRealtimeNanos() - started
            val parcel = Parcel.obtain()
            try {
                remote.writeToParcel(parcel, 0)
                parcelSize = parcel.dataSize()
            } finally { parcel.recycle() }
        }
        val median = times.drop(2).sorted()[5] / 1_000_000.0
        report.append("${if (native) "Native" else "Bitmap"}: median=${median}ms, parcel=$parcelSize bytes\n")
    }
}

private fun sizeOptions(width: Int, height: Int) = Bundle().apply {
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
}

internal fun HyperOsComparison.fontSizes() {
    for (font in listOf(1f, 1.3f, 2f)) for ((width, height) in comparisonSizes) {
        val sized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = font })
        // Fill both orientations' keys so the result does not depend on how the device is held.
        val root = renderer.renderNative(sized, 0, record, false, sizeOptions(width, height)).apply(sized, FrameLayout(sized))
        if (root.findViewById<View>(R.id.status_card_image) == null) {
            val density = sized.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val headerSize = 12f * density * referenceScale(width.toFloat(), height.toFloat())
            for (id in listOf(R.id.status_data_time, R.id.status_refresh)) {
                val text = root.findViewById<TextView>(id)
                check(kotlin.math.abs(text.textSize - headerSize) < .01f) {
                    "Header text ${text.textSize}px != ${headerSize}px for $id at ${width}x$height, font $font, density $density"
                }
                check(text.layout.getEllipsisCount(0) == 0 && text.layout.getLineWidth(0) <= text.width + 1f) {
                    "Header clipped at ${width}x$height: ${text.text}"
                }
            }
            val refresh = root.findViewById<View>(R.id.status_refresh_arrow)
            check(kotlin.math.abs(refresh.width - headerSize) <= 1f && kotlin.math.abs(refresh.height - headerSize) <= 1f)
            for (id in listOf(R.id.status_sanity, R.id.status_drone, R.id.status_native_training_name, R.id.status_recruit,
                R.id.status_fatigue, R.id.status_orders, R.id.status_manufacture, R.id.status_jade, R.id.status_daily,
                R.id.status_rest, R.id.status_tower)) {
                val text = root.findViewById<TextView>(id)
                check(text.layout.getEllipsisCount(0) == 0 && text.layout.getLineWidth(0) <= text.width + 1f) {
                    "Clipped $font / ${width}x$height: ${text.text}"
                }
            }
        }
        check(root.findViewById<View>(R.id.status_card_image) == null)
        cases++
    }
}

// A card squeezed below its design size keeps every element inside the content padding.
internal fun HyperOsComparison.smallSizes() {
    for (theme in WidgetTheme.entries) {
        val root = renderer.renderAtSize(context, 0, record.copy(config = config.copy(theme = theme)), false,
            336f, 152f, native = true).apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((317 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((143 * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val content = root.findViewById<View>(R.id.status_native_content)
        check(content.width == root.width && content.height == root.height && content.scaleX == 1f && content.scaleY == 1f)
        for (id in listOf(R.id.status_avatar, R.id.status_sanity_icon, R.id.status_refresh_arrow,
            R.id.status_jade, R.id.status_tower, R.id.status_jade_label, R.id.status_tower_label)) {
            val view = root.findViewById<View>(id)
            val bounds = android.graphics.Rect(0, 0, view.width, view.height)
            (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(view, bounds)
            check(bounds.left >= content.paddingLeft && bounds.right <= root.width - content.paddingRight)
            if (view is TextView) check(view.layout.getEllipsisCount(0) == 0 && view.layout.getLineWidth(0) <= view.width + 1f)
        }
        cases++
    }
    check(renderer.renderNative(context, 0, record, false, sizeOptions(280, 110)).apply(context, FrameLayout(context))
        .findViewById<View>(R.id.status_card_image) == null)
    val normal = Bundle().apply {
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(android.util.SizeF(336f, 152f), android.util.SizeF(360f, 162f)))
    }
    check(renderer.renderNative(context, 0, record, false, normal).apply(context, FrameLayout(context))
        .findViewById<View>(R.id.status_title) != null)
    cases += 2
}
