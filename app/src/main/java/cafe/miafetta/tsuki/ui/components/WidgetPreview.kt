package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.core.*

// What every widget preview needs from the state layer: the renderer (snapshot, dark palette, Branding game ID), the shared
// bitmap cache and the refresh version.
@Immutable
internal class WidgetPreviews(val render: suspend (WidgetSnapshot?, Boolean, String) -> ImageBitmap?,
    val cache: LruCache<String, ImageBitmap>, val version: Int)

// One preview that may be shown later, e.g. on the other tab of the Widgets page.
@Immutable
internal data class PreviewTarget(val snapshot: WidgetSnapshot?, val dark: Boolean, val previewId: String,
    val game: String = Branding.GAME_ID)

// Overview's empty state shows the preview on a tinted stage. The Widgets page and the widget sheet show the same image
// without a stage, through ScaledWidgetPreview.
@Composable
internal fun WidgetPreviewStage(previews: WidgetPreviews, snapshot: WidgetSnapshot?, dark: Boolean, previewId: String,
    modifier: Modifier = Modifier, imageModifier: Modifier = Modifier, game: String = Branding.GAME_ID) {
    WidgetStage(modifier) { WidgetPreviewImage(previews, snapshot, dark, previewId, imageModifier.padding(4.dp), game) }
}

// Renders previews that are not on screen yet into the shared cache, so they appear at once and current when shown,
// instead of an empty stage or an image rendered before the data arrived. The cache key does not include the snapshot,
// so every change of data or version renders them again.
@Composable
internal fun WarmWidgetPreviews(previews: WidgetPreviews, targets: List<PreviewTarget>) {
    val night = isNightConfiguration()
    LaunchedEffect(targets, previews.version, night) {
        targets.forEach { target ->
            val key = previewKey("${target.game}:${target.previewId}", target.dark, night)
            previews.render(target.snapshot, target.dark, target.game)?.let { previews.cache.put(key, it) }
        }
    }
}

@Composable
private fun isNightConfiguration() = LocalConfiguration.current.uiMode and
    android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

private fun previewKey(previewId: String, dark: Boolean, night: Boolean) = "$previewId:$dark:$night"

private val PreviewShape = RoundedCornerShape(percent = 15)
// Measured against HyperOS's own widget previews: a faint black falloff, slightly heavier below, that leaves the edge crisp.
private val PreviewShadowRadius = 20.dp
private val PreviewShadowOffset = 6.dp
private const val PreviewShadowLight = .10f
private const val PreviewShadowDark = .30f
internal const val PreviewFadeMillis = 180

// How far the shadow reaches past the preview: dropShadow ends one radius outside the offset shape. Whatever fades a
// preview through an offscreen layer must hold this room, or the shadow is cut off at the layer's edge while it fades.
internal val PreviewShadowAbove = PreviewShadowRadius - PreviewShadowOffset
internal val PreviewShadowBelow = PreviewShadowRadius + PreviewShadowOffset
internal val PreviewShadowSide = PreviewShadowRadius

// The rendered widget card, as wide as [modifier] allows: the bitmap carries the card's own corners, the painted shadow
// sits around it, and a bitmap that arrives late fades in. Every in-app widget preview draws through this.
@Composable
internal fun WidgetPreviewImage(previews: WidgetPreviews, snapshot: WidgetSnapshot?, dark: Boolean, previewId: String,
    modifier: Modifier, game: String) {
    val cacheKey = previewKey("$game:$previewId", dark, isNightConfiguration())
    val image by key(cacheKey) {
        produceState(previews.cache.get(cacheKey), snapshot, previews.version) {
            previews.cache.get(cacheKey)?.let { value = it }
            val rendered = previews.render(snapshot, dark, game)
            if (rendered != null) {
                previews.cache.put(cacheKey, rendered)
                value = rendered
            }
        }
    }
    val description = (snapshot?.title ?: "未同步角色") + "。" +
        (snapshot?.metrics?.joinToString("，") { it.display().replace("≈", "") } ?: "所有指标未知")
    val previewModifier = modifier.fillMaxWidth().aspectRatio(1028f / 462f)
    // A painted drop shadow rather than an elevation shadow: elevation shadows follow the light source, so the same preview
    // looked different on the Widgets page and lower down in the editor sheet.
    val shadowAlpha = if (isTsukiDarkTheme()) PreviewShadowDark else PreviewShadowLight
    val current = image
    // A preview that was not cached when the stage appeared fades in rather than popping into the empty stage. The fade
    // modulates each draw instead of compositing offscreen: an offscreen layer is cut to the image, which hid the shadow
    // until the fade ended and then let it pop in.
    val reveal = remember(cacheKey) { Animatable(if (current != null) 1f else 0f) }
    LaunchedEffect(reveal, current != null) { if (current != null) reveal.animateTo(1f, tween(PreviewFadeMillis)) }
    if (current != null) {
        Image(current, description, previewModifier.graphicsLayer {
            alpha = reveal.value
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }.dropShadow(PreviewShape) {
            radius = PreviewShadowRadius.toPx()
            offset = Offset(0f, PreviewShadowOffset.toPx())
            color = Color.Black
            alpha = shadowAlpha
        })
    } else Spacer(previewModifier)
}
