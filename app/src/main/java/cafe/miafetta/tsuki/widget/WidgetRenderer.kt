package cafe.miafetta.tsuki.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.TrainingState
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTheme
import cafe.miafetta.tsuki.core.WidgetTraining
import cafe.miafetta.tsuki.providers.AvatarCache
import cafe.miafetta.tsuki.providers.OfficialAvatars

// One render pass: the RemoteViews being filled and the view IDs that differ between the native and the bitmap layouts.
internal class CardViews(val context: Context, val views: RemoteViews, val light: Boolean, val native: Boolean) {
    val trainingName = if (native) R.id.status_native_training_name else R.id.status_training
    val trainingTime = if (native) R.id.status_native_training_time else R.id.status_training_time
    val trainingAvatar = if (native) R.id.status_native_training_avatar else R.id.status_trainee
}

internal class StatusWidgetRenderer(private val avatars: AvatarCache) {
    internal fun preview(context: Context, snapshot: WidgetSnapshot?, theme: WidgetTheme,
        feature: String = WidgetRuntime.FEATURE): Bitmap {
        val referenceContext = referenceContext(context)
        val views = if (feature == WidgetRuntime.ENDFIELD_FEATURE) EndfieldCard.render(referenceContext, 0, null, false,
            REFERENCE_WIDTH, REFERENCE_HEIGHT, snapshot)
            else renderAtSize(referenceContext, 0, null, false, REFERENCE_WIDTH, REFERENCE_HEIGHT,
                snapshotOverride = snapshot, themeOverride = theme, native = true)
        val content = layoutReference(referenceContext, views)
        return createBitmap(1028, 462).also { content.draw(Canvas(it)) }
    }

    fun render(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean = false): RemoteViews =
        renderNative(context, id, record, storageError, AppWidgetManager.getInstance(context).getAppWidgetOptions(id))

    internal fun renderNative(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean, options: Bundle): RemoteViews {
        val loading = showsLoadingPlaceholder(record, storageError)
        // The instance's provider decides the game; a stored configuration always matches it (WidgetStorageProvider).
        val endfield = (record?.config?.featureId ?: StatusWidgetProvider.feature(context, id)) == WidgetRuntime.ENDFIELD_FEATURE
        return sizedViews(context, options) { width, height ->
            when {
                endfield && loading -> EndfieldCard.loading(context, id, record, storageError, width, height)
                endfield -> EndfieldCard.render(context, id, record, storageError, width, height)
                loading -> renderLoading(context, id, record, storageError, width, height)
                else -> renderAtSize(context, id, record, storageError, width, height, native = true)
            }
        }
    }

    // One card for each size the host reports, or one for the cell its legacy min/max options describe.
    private fun sizedViews(context: Context, options: Bundle, atSize: (Float, Float) -> RemoteViews): RemoteViews {
        val sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.filter { it.width.isFinite() && it.height.isFinite() && it.width in 1f..800f && it.height in 1f..800f }
            ?.distinct()?.take(16).orEmpty()
        if (sizes.size == 1) return atSize(sizes.single().width, sizes.single().height)
        if (sizes.isNotEmpty()) return RemoteViews(sizes.associateWith { atSize(it.width, it.height) })
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        val heightKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        val width = options.getInt(widthKey, 360)
        val height = options.getInt(heightKey, 162)
        return atSize(width.takeIf { it > 0 }?.toFloat() ?: 360f, height.takeIf { it > 0 }?.toFloat() ?: 162f)
    }

    // The placeholder card at one size, scaled like the native card that later replaces it.
    private fun renderLoading(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean,
                              width: Float, height: Float): RemoteViews {
        val theme = record?.config?.theme ?: StatusWidgetProvider.theme(context, id)
        val views = RemoteViews(context.packageName, loadingLayout(theme))
        sizeLoadingContent(views, context, width, height)
        bindWidgetActions(context, id, views, widgetTapTarget(record, storageError))
        return views
    }

    // The launcher has a separate XML/action renderer: send only the finished reference canvas.
    internal fun renderWithSurface(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean,
                                   hostBlur: Boolean): RemoteViews {
        val theme = record?.config?.theme ?: StatusWidgetProvider.theme(context, id)
        val referenceContext = referenceContext(context)
        val content = layoutReference(referenceContext,
            renderAtSize(referenceContext, id, record, storageError, REFERENCE_WIDTH, REFERENCE_HEIGHT))
        val bitmap = createBitmap(1028, 462, Bitmap.Config.ARGB_8888)
        bitmap.density = Bitmap.DENSITY_NONE
        // Keep the legacy comparison opaque when its host does not provide blur.
        val background = content.findViewById<View>(android.R.id.background)
        background.background = referenceContext.getDrawable(if (theme == WidgetTheme.LIGHT)
            R.drawable.widget_host_light else R.drawable.widget_host_dark)?.mutate()?.apply {
            alpha = if (hostBlur) materialTintAlpha(theme) else 255
        }
        val canvas = Canvas(bitmap)
        content.draw(canvas)
        if (!hostBlur) drawSurfaceRim(canvas, theme == WidgetTheme.LIGHT)
        val views = RemoteViews(context.packageName, bitmapThemeLayout(theme))
        views.setImageViewBitmap(R.id.status_card_image, bitmap)
        views.setContentDescription(android.R.id.background, background.contentDescription)
        views.setContentDescription(R.id.status_refresh_hit, content.findViewById<View>(R.id.status_refresh_hit).contentDescription)
        bindWidgetActions(context, id, views, widgetTapTarget(record, storageError))
        return views
    }

    internal fun renderAtSize(context: Context, id: Int, record: WidgetRecord?, storageError: Boolean,
                              width: Float, height: Float, snapshotOverride: WidgetSnapshot? = null,
                              themeOverride: WidgetTheme? = null, native: Boolean = false): RemoteViews {
        val theme = themeOverride ?: record?.config?.theme ?: StatusWidgetProvider.theme(context, id)
        val light = theme == WidgetTheme.LIGHT
        val layout = if (!native) themeLayout(theme)
            else if (light) R.layout.status_widget_native_v30_light else R.layout.status_widget_native_v30_dark
        val card = CardViews(context, RemoteViews(context.packageName, layout), light, native)
        val snapshot = snapshotOverride ?: record?.snapshot
        val now = System.currentTimeMillis()
        val training = snapshot?.training ?: WidgetTraining()
        card.bindSurface()
        card.bindPalette()
        bindAvatars(card, snapshot)
        card.views.setTextViewText(R.id.status_title, snapshot?.let { "Dr ${it.title}" } ?: context.getString(R.string.status_unconfigured))
        card.bindPrimaryMetrics(snapshot, now)
        card.bindTraining(training, now)
        card.bindSummary(snapshot)
        card.bindProgress(snapshot)
        val status = cardStatus(record, storageError)
        val failure = record?.failure?.let { context.getString(cafe.miafetta.tsuki.failureMessage(it.name)) }.orEmpty()
        val time = card.bindStatus(snapshot, training, now, status, failure)
        bindWidgetActions(context, id, card.views, widgetTapTarget(record, storageError))
        card.views.setContentDescription(R.id.status_refresh_hit,
            listOf("刷新", status, "数据时间 $time").filter { it.isNotEmpty() }.joinToString("。"))
        val scale = referenceScale(width, height)
        card.bindTextSizes(if (native) scale else 1f)
        if (native) {
            sizeNativeContent(card.views, context, scale)
            card.bindNativeDescriptions(snapshot, training, now, listOf(status, "数据时间 $time", failure))
        }
        val root = if (native) R.id.status_native_content else android.R.id.background
        card.views.setFloat(root, "setScaleX", if (native) 1f else scale)
        card.views.setFloat(root, "setScaleY", if (native) 1f else scale)
        return card.views
    }

    private fun bindAvatars(card: CardViews, snapshot: WidgetSnapshot?) {
        bindAvatar(card, R.id.status_avatar, snapshot?.avatarUrl)
        val trainee = snapshot?.training?.takeIf { it.traineeId != null }
        bindAvatar(card, card.trainingAvatar, trainee?.avatarUrl ?: trainee?.traineeId?.let(OfficialAvatars::character))
        val idle = snapshot?.training?.state in listOf(TrainingState.IDLE, TrainingState.COMPLETE)
        if (trainee != null || !idle) return
        if (card.light) card.views.setIcon(card.trainingAvatar, "setImageIcon",
            Icon.createWithBitmap(trainingIdleIcon(card.context, true)), Icon.createWithBitmap(trainingIdleIcon(card.context, false)))
        else card.views.setImageViewBitmap(card.trainingAvatar, trainingIdleIcon(card.context, false))
        card.views.setInt(card.trainingAvatar, "setBackgroundResource", 0)
    }

    private fun bindAvatar(card: CardViews, viewId: Int, url: String?) {
        val views = card.views
        views.setInt(viewId, "setBackgroundResource", 0)
        views.setImageViewResource(viewId, if (card.light) R.drawable.avatar_placeholder_light else R.drawable.avatar_placeholder_dark)
        val bytes = avatars.read(url) ?: return
        val bitmap = decodeAvatar(bytes) ?: return
        val rounded = roundedAvatar(bitmap, if (viewId == R.id.status_avatar) 1f / 5.4f else 1f / 9.5f)
        if (viewId == card.trainingAvatar && card.light) {
            // Light and night variants: the portrait mark colour follows the system mode.
            views.setIcon(viewId, "setImageIcon",
                Icon.createWithBitmap(trainingPortrait(card.context, rounded.copy(Bitmap.Config.ARGB_8888, true), true)),
                Icon.createWithBitmap(trainingPortrait(card.context, rounded, false)))
        } else {
            views.setImageViewBitmap(viewId,
                if (viewId == card.trainingAvatar) trainingPortrait(card.context, rounded, card.light) else rounded)
        }
    }

    // The 360dp reference card is laid out at a fixed density and font scale, independent of the device.
    private fun referenceContext(context: Context) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            densityDpi = 457
            fontScale = 1f
        })

    private fun layoutReference(context: Context, views: RemoteViews): View {
        val content = views.apply(context, FrameLayout(context))
        content.measure(View.MeasureSpec.makeMeasureSpec(1028, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(462, View.MeasureSpec.EXACTLY))
        content.layout(0, 0, 1028, 462)
        return content
    }
}
