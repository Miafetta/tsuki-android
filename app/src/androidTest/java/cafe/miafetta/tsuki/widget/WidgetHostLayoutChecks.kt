package cafe.miafetta.tsuki.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.*

// Layout checks on a card applied to a real AppWidgetHostView; `root` is the card's android.R.id.background view.

internal fun checkScaledRoot(host: AppWidgetHostView, root: View, height: Int) {
    check(kotlin.math.abs(root.scaleX - referenceScale(360f, height.toFloat())) < .0001f)
    check(root.scaleX == root.scaleY) { "Nonuniform card scale" }
    val rendered = android.graphics.RectF(0f, 0f, root.width.toFloat(), root.height.toFloat())
    root.matrix.mapRect(rendered)
    rendered.offset(root.left.toFloat(), root.top.toFloat())
    check(rendered.left >= -1f && rendered.top >= -1f &&
        rendered.right <= host.width + 1f && rendered.bottom <= host.height + 1f) { "Scaled card outside host" }
}

private fun backgroundColor(context: Context, theme: WidgetTheme) =
    context.getColor(if (theme == WidgetTheme.LIGHT) R.color.widget_background_light else R.color.widget_background_dark)

internal fun checkSurfaceAndAvatar(context: Context, root: View, theme: WidgetTheme) {
    val background = androidx.core.graphics.createBitmap(root.width, root.height)
    root.background.setBounds(0, 0, root.width, root.height)
    root.background.draw(android.graphics.Canvas(background))
    val center = background.getPixel(background.width / 2, background.height / 2)
    val base = backgroundColor(context, theme)
    check(Color.alpha(center) == materialTintAlpha(theme))
    for (shift in listOf(0, 8, 16)) check(kotlin.math.abs(
        ((center shr shift) and 255) - ((base shr shift) and 255)) <= 1)
    check(root.findViewById<ImageView>(R.id.status_avatar).drawable is BitmapDrawable)
    val avatar = (root.findViewById<ImageView>(R.id.status_avatar).drawable as BitmapDrawable).bitmap
    check(Color.alpha(avatar.getPixel(0, 0)) < 128) { "Avatar has square corners" }
    check(avatar.getPixel(avatar.width / 2, avatar.height / 2) == Color.MAGENTA)
    check(root.findViewById<ImageView>(R.id.status_trainee).drawable is BitmapDrawable)
}

internal fun checkThemeColors(context: Context, root: View, theme: WidgetTheme) {
    val textColor = context.getColor(if (theme == WidgetTheme.LIGHT) R.color.widget_text_light
        else R.color.widget_text_dark)
    for (id in listOf(R.id.status_title, R.id.status_sanity, R.id.status_drone, R.id.status_training)) {
        check(root.findViewById<TextView>(id).currentTextColor == textColor) { "Mixed theme" }
    }
    val summaryColor = context.getColor(if (theme == WidgetTheme.LIGHT) R.color.widget_accent_light
        else R.color.widget_accent_dark)
    for (id in listOf(R.id.status_recruit, R.id.status_orders, R.id.status_manufacture, R.id.status_fatigue,
        R.id.status_jade, R.id.status_tower, R.id.status_daily, R.id.status_rest)) {
        check(root.findViewById<TextView>(id).currentTextColor == summaryColor) { "Mixed summary theme" }
    }
    val secondary = context.getColor(if (theme == WidgetTheme.LIGHT) R.color.widget_secondary_light
        else R.color.widget_secondary_dark)
    for (id in listOf(R.id.status_sanity_time, R.id.status_drone_time, R.id.status_training_time,
        R.id.status_data_time)) {
        val lightTime = id == R.id.status_data_time && theme == WidgetTheme.LIGHT
        check(root.findViewById<TextView>(id).currentTextColor ==
            if (lightTime) context.getColor(R.color.widget_time_light) else secondary)
    }
    check(root.findViewById<TextView>(R.id.status_refresh).currentTextColor == context.getColor(
        if (theme == WidgetTheme.LIGHT) R.color.widget_accent_light else R.color.widget_accent_dark))
    val refreshText = root.findViewById<TextView>(R.id.status_refresh)
    val refreshIcon = root.findViewById<ImageView>(R.id.status_refresh_arrow)
    check(kotlin.math.abs(refreshText.top + refreshText.height / 2f -
        refreshIcon.top - refreshIcon.height / 2f) <= 1f) { "Refresh icon is not vertically centered" }
    check(refreshIcon.colorFilter != null) { "Refresh icon has no theme tint" }
}

internal fun checkProgressLines(root: View, snapshot: WidgetSnapshot) {
    for ((id, label) in listOf(R.id.status_jade_progress to "合成玉", R.id.status_tower_progress to "保全酬劳")) {
        val line = root.findViewById<ImageView>(id)
        val level = progressLevel(cardMetric(snapshot, label))
        check(line.visibility == if (level == null) View.INVISIBLE else View.VISIBLE)
        check(line.drawable.level == (level ?: 0))
    }
}

// `case` describes the density, font scale, palette, host height and refresh state for failure messages.
internal fun checkSummaryRows(root: View, case: String) {
    val summary = root.findViewById<android.widget.GridLayout>(R.id.status_summary)
    val cells = listOf(R.id.status_recruit_cell, R.id.status_fatigue_cell, R.id.status_orders_cell,
        R.id.status_jade_cell, R.id.status_daily_cell, R.id.status_rest_cell, R.id.status_manufacture_cell,
        R.id.status_tower_cell)
    val bounds = cells.map { id ->
        val cell = summary.findViewById<View>(id)
        Rect(0, 0, cell.width, cell.height).also {
            summary.offsetDescendantRectToMyCoords(cell, it)
            check(it.top >= 0 && it.bottom <= summary.height) {
                "Summary row clipped: $id bounds=$it summaryHeight=${summary.height} $case"
            }
        }
    }
    check(bounds.take(4).map { it.top }.distinct().size == 1) { "First row baseline shifted" }
    check(bounds.drop(4).map { it.top }.distinct().size == 1) { "Second row baseline shifted" }
    check(bounds.take(4).maxOf { it.bottom } <= bounds.drop(4).minOf { it.top }) { "Summary rows overlap" }
    for (id in listOf(R.id.status_jade_progress, R.id.status_tower_progress)) {
        val line = summary.findViewById<View>(id)
        val rect = Rect(0, 0, line.width, line.height)
        summary.offsetDescendantRectToMyCoords(line, rect)
        check(rect.bottom <= summary.height && rect.top >= 0) { "Progress line clipped" }
    }
}

internal fun checkValueTexts(root: View, snapshot: WidgetSnapshot, scale: Float, height: Int) {
    for (id in listOf(R.id.status_sanity, R.id.status_sanity_time, R.id.status_drone,
        R.id.status_drone_time, R.id.status_training, R.id.status_training_time,
        R.id.status_recruit, R.id.status_orders, R.id.status_manufacture, R.id.status_fatigue,
        R.id.status_jade, R.id.status_tower, R.id.status_daily, R.id.status_rest, R.id.status_data_time)) {
        val text = root.findViewById<TextView>(id)
        val rect = Rect(0, 0, text.width, text.height)
        (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(text, rect)
        check(rect.top >= 0 && rect.bottom <= root.height) { "Outside 4x2 height: $id" }
        check(text.width > 0 && text.height > 0 && text.text.isNotEmpty()) { "Missing value: $id" }
        check(!text.text.contains("≈")) { "Approximation sign still visible: $id" }
        val layout = requireNotNull(text.layout)
        check(layout.lineCount == 1)
        if (id == R.id.status_training) check(text.text.toString() == snapshot.training.name)
        else check(layout.getEllipsisCount(0) == 0 && layout.getLineEnd(0) == text.text.length) {
            "Truncated value: $id"
        }
        check(layout.getLineWidth(0) <= text.width - text.totalPaddingLeft - text.totalPaddingRight + 1) {
            "Overflow: $id"
        }
        checkGlyphs(text, "scale=$scale height=$height id=$id")
    }
}

// Font metrics include unused ascent/descent; check visible glyphs at their actual baseline.
private fun checkGlyphs(text: TextView, case: String) {
    val styled = text.text as? android.text.Spanned
    for (index in text.text.indices) {
        val paint = android.text.TextPaint(text.paint)
        styled?.getSpans(index, index + 1, android.text.style.MetricAffectingSpan::class.java)
            ?.forEach { it.updateMeasureState(paint) }
        val glyph = Rect()
        paint.getTextBounds(text.text.toString(), index, index + 1, glyph)
        check(text.baseline + glyph.top >= 0 && text.baseline + glyph.bottom <= text.height) {
            "Glyph clipped: $case index=$index baseline=${text.baseline} glyph=$glyph box=${text.height}"
        }
    }
}

internal fun checkSummaryLabels(root: View) {
    val summary = root.findViewById<android.widget.GridLayout>(R.id.status_summary)
    for (id in listOf(R.id.status_recruit_label, R.id.status_fatigue_label, R.id.status_orders_label,
        R.id.status_jade_label, R.id.status_daily_label, R.id.status_rest_label,
        R.id.status_manufacture_label, R.id.status_tower_label)) {
        val text = summary.findViewById<TextView>(id)
        val rect = Rect(0, 0, text.width, text.height)
        summary.offsetDescendantRectToMyCoords(text, rect)
        check(rect.top >= 0 && rect.bottom <= summary.height) { "Label clipped: $id" }
    }
}

internal fun checkCardTexts(root: View, snapshot: WidgetSnapshot, record: WidgetRecord) {
    check(root.findViewById<TextView>(R.id.status_sanity).text.toString() == "84/135")
    check(root.findViewById<TextView>(R.id.status_data_time).text.toString() ==
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(snapshot.dataAt)))
    check(root.contentDescription.contains(cardStatus(record)))
    check(root.findViewById<TextView>(R.id.status_rest_label).text == "休息进度")
}

// The bitmap fallback: the card image carries its own surface, keeps its aspect ratio and lines up the refresh hit area.
internal fun checkImageHost(context: Context, renderer: StatusWidgetRenderer, provider: AppWidgetProviderInfo,
    record: WidgetRecord, height: Int) {
    val density = context.resources.displayMetrics.density
    val imageHost = AppWidgetHostView(context)
    imageHost.setAppWidget(1, provider)
    imageHost.setPadding(0, 0, 0, 0)
    val imageRemote = renderer.renderWithSurface(context, 1, record, false, false)
    imageRemote.apply(context, imageHost)
    imageHost.updateAppWidget(imageRemote)
    measureHost(imageHost, density, height)
    val image = requireNotNull(imageHost.findViewById<ImageView>(R.id.status_card_image))
    val card = (image.drawable as BitmapDrawable).bitmap
    check(card.width == 1028 && card.height == 462)
    val expectedBackground = backgroundColor(context, record.config.theme)
    check(card.getPixel(514, 100) == expectedBackground) { "Card has no independent theme background" }
    val hostBackground = requireNotNull(imageHost.findViewById<View>(android.R.id.background).background)
    val surface = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    hostBackground.setBounds(0, 0, 32, 32)
    hostBackground.draw(android.graphics.Canvas(surface))
    check(surface.getPixel(16, 16) == expectedBackground)
    // Reproduce a host dropping its root background: the image must still supply the card surface.
    imageHost.findViewById<View>(android.R.id.background).background = null
    val renderedHost = Bitmap.createBitmap(imageHost.width, imageHost.height, Bitmap.Config.ARGB_8888)
    imageHost.draw(android.graphics.Canvas(renderedHost))
    val surfacePoint = floatArrayOf(514f, 100f)
    image.imageMatrix.mapPoints(surfacePoint)
    val surfaceOffset = Rect(0, 0, image.width, image.height)
    imageHost.offsetDescendantRectToMyCoords(image, surfaceOffset)
    check(renderedHost.getPixel((surfacePoint[0] + surfaceOffset.left).toInt(),
        (surfacePoint[1] + surfaceOffset.top).toInt()) == expectedBackground) {
        "Host background removal made the card transparent"
    }
    val transform = FloatArray(9)
    image.imageMatrix.getValues(transform)
    check(kotlin.math.abs(transform[0] - transform[4]) < .0001f) { "Nonuniform image scale" }
    val imageBounds = android.graphics.RectF(image.drawable.bounds)
    image.imageMatrix.mapRect(imageBounds)
    check(imageBounds.left >= -1 && imageBounds.top >= -1 && imageBounds.right <= image.width + 1 &&
        imageBounds.bottom <= image.height + 1) { "Bitmap cropped" }
    checkRefreshHit(imageHost, image)
    check(imageHost.findViewById<View>(android.R.id.background).contentDescription.contains(cardStatus(record)))
}

private fun checkRefreshHit(imageHost: AppWidgetHostView, image: ImageView) {
    check(imageHost.findViewById<View>(android.R.id.background).hasOnClickListeners())
    val hit = requireNotNull(imageHost.findViewById<View>(R.id.status_refresh_hit))
    check(hit.hasOnClickListeners() && hit.width > 0 && hit.height > 0) { "Refresh hit missing" }
    val hitBounds = Rect(0, 0, hit.width, hit.height)
    imageHost.offsetDescendantRectToMyCoords(hit, hitBounds)
    val imageOffset = Rect(0, 0, image.width, image.height)
    imageHost.offsetDescendantRectToMyCoords(image, imageOffset)
    val refreshPoint = floatArrayOf(image.drawable.bounds.width() * .93f, image.drawable.bounds.height() * .06f)
    image.imageMatrix.mapPoints(refreshPoint)
    check(hitBounds.contains((refreshPoint[0] + imageOffset.left).toInt(),
        (refreshPoint[1] + imageOffset.top).toInt())) { "Refresh hit not over reference button" }
}
