package cafe.miafetta.tsuki.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import cafe.miafetta.tsuki.R

// Bitmaps drawn for the widget: rounded avatars, the training placeholder and portrait mark, and the bitmap-path rim.

private fun smoothPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

// Opaque equivalent of official widget_training_room_bg (#BFFFFFFF over #363636) so all 4 frame edges have identical pixels.
private fun frameColor(light: Boolean) = if (light) Color.rgb(100, 103, 103) else Color.rgb(205, 205, 205)

internal fun roundedAvatar(source: Bitmap, radiusRatio: Float): Bitmap {
    val size = minOf(source.width, source.height).coerceAtMost(128)
    val result = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = smoothPaint()
    val canvas = Canvas(result)
    val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
    canvas.drawRoundRect(bounds, size * radiusRatio, size * radiusRatio, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    val square = minOf(source.width, source.height)
    val pixels = IntArray(square * square)
    source.getPixels(pixels, 0, square, (source.width - square) / 2, (source.height - square) / 2, square, square)
    val inset = bakedFrameInset(square, pixels)
    val cropSize = square - inset * 2
    val left = (source.width - square) / 2 + inset
    val top = (source.height - square) / 2 + inset
    canvas.drawBitmap(source, Rect(left, top, left + cropSize, top + cropSize), bounds, paint)
    return result
}

internal fun decodeAvatar(bytes: ByteArray): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048)
    val options = BitmapFactory.Options().apply {
        inSampleSize = Integer.highestOneBit((minOf(bounds.outWidth, bounds.outHeight) / 64).coerceAtLeast(1))
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()

internal fun drawSurfaceRim(canvas: Canvas, light: Boolean) {
    val bounds = RectF(2f, 2f, canvas.width - 2f, canvas.height - 2f)
    val radius = canvas.width * .066f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        shader = LinearGradient(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(),
            if (light) intArrayOf(0x99ffffff.toInt(), 0x18ffffff, 0x30434b48)
            else intArrayOf(0x18ffffff, 0x04ffffff, 0x14000000),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        strokeWidth = 3f
        maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
    }
    // Draw locally: the launcher does not guarantee a material outline for bitmap widgets.
    canvas.drawRoundRect(bounds, radius, radius, paint)
    paint.maskFilter = null
    paint.strokeWidth = 1.5f
    canvas.drawRoundRect(bounds, radius, radius, paint)
}

internal fun trainingIdleIcon(context: Context, light: Boolean): Bitmap {
    val source = BitmapFactory.decodeResource(context.resources,
        if (light) R.drawable.widget_training_v2_light else R.drawable.widget_training_v2_dark)
    val result = createBitmap(98, 98, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = smoothPaint()
    // Retain the interior and draw one uniform frame matching the official 0.5dp border.
    canvas.drawBitmap(source, Rect(2, 2, source.width - 2, source.height - 2), RectF(0f, 0f, 98f, 98f), paint)
    paint.color = frameColor(light)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawRect(1f, 1f, 97f, 97f, paint)
    return result
}

internal fun trainingPortrait(context: Context, avatar: Bitmap, light: Boolean): Bitmap {
    val canvas = Canvas(avatar)
    val size = avatar.width.toFloat()
    val paint = smoothPaint()
    paint.color = frameColor(light)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * 2f / 98f
    val inset = paint.strokeWidth / 2f
    canvas.drawRect(inset, inset, size - inset, size - inset, paint)
    val badge = BitmapFactory.decodeResource(context.resources, R.drawable.widget_training_badge_dark)
    paint.style = Paint.Style.FILL
    paint.colorFilter = PorterDuffColorFilter(if (light) Color.rgb(75, 75, 75) else Color.WHITE, PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(badge, null, RectF(0f, size * .625f, size * .406f, size), paint)
    badge.recycle()
    return avatar
}
