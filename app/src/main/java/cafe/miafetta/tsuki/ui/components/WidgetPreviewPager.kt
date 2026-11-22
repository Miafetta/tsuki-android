package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.widgetCatalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Calibrated to HyperOS 4 add-widget swipe recordings (90fps, 3.25 px/dp): touch-on-card sink (~56ms to 0.93x) and spring
// rebound (~160ms), ~330ms pager snap, incoming 3D Y tilt, and worm pagination dots spanning adjacent dots at midpoint.
internal object WidgetPagerMotion {
    const val TouchSinkScale = .93f
    const val PressDamping = .85f
    const val PressStiffness = 1200f
    const val ReleaseDamping = .75f
    const val ReleaseStiffness = 480f
    const val SnapDamping = .92f
    const val SnapStiffness = 550f
    const val SnapVisibilityThresholdPx = .5f
    const val AnchorSettleEpsilon = .005f
    const val TiltMaxDegrees = 45f
    const val CameraDistance = 3.5f
    const val CardFadeAmount = .5f
    // VerticalBuffer sits inside each page so shadow and 3D tilt edges are never clipped while keeping swipe room around the card.
    val VerticalBuffer = 36.dp
    val DotSize = 4.5.dp
    val DotPitch = 11.dp
    val DotGapAbove = 12.dp
    val DotGapBelow = 31.5.dp
    const val DotInactiveAlpha = .20f
    const val DotActiveAlpha = .70f
}

internal fun widgetCatalogIndex(game: String, theme: String): Int =
    widgetCatalog.indexOfFirst { it.first == game && it.second == theme }.takeIf { it >= 0 }
        ?: widgetCatalog.indexOfFirst { it.first == game }.takeIf { it >= 0 }
        ?: 0

internal fun pagerPageOffset(page: Int, currentPage: Int, currentPageOffsetFraction: Float): Float =
    (page - currentPage) - currentPageOffsetFraction

// The outgoing anchored card stays flat as it slides out; the incoming adjacent card tilts away along its inner edge.
internal fun incomingPageTilt(page: Int, anchorPage: Int, pageOffset: Float): Float =
    if (page == anchorPage) 0f else -pageOffset.coerceIn(-1f, 1f) * WidgetPagerMotion.TiltMaxDegrees

// Both outgoing and incoming cards linearly fade from 1.0 at rest to 0.5 at one page away (measured from HyperOS).
internal fun pageCardAlpha(pageOffset: Float): Float =
    1f - WidgetPagerMotion.CardFadeAmount * abs(pageOffset).coerceIn(0f, 1f)

// Header stays opaque across palettes of the same game and cross-fades around midpoint when switching games.
internal fun crossGameAlpha(pageIndex: Int, offsetFraction: Float, catalog: List<Pair<String, String>> = widgetCatalog): Float {
    if (catalog.isEmpty()) return 1f
    val clamped = pageIndex.coerceIn(0, catalog.lastIndex)
    val next = (clamped + if (offsetFraction > 0f) 1 else -1).coerceIn(0, catalog.lastIndex)
    return if (catalog[clamped].first == catalog[next].first) 1f else (1f - abs(offsetFraction) * 2f).coerceIn(0f, 1f)
}

// Settings rows fade out toward the midpoint and fade back in on every page switch, staying opaque on edge overscroll.
internal fun pageSettingsAlpha(pageIndex: Int, offsetFraction: Float, pageCount: Int = widgetCatalog.size): Float {
    if (pageCount <= 1) return 1f
    val clamped = pageIndex.coerceIn(0, pageCount - 1)
    val next = (clamped + if (offsetFraction > 0f) 1 else -1).coerceIn(0, pageCount - 1)
    return if (clamped == next) 1f else (1f - abs(offsetFraction) * 2f).coerceIn(0f, 1f)
}

internal fun wormSpan(position: Float, count: Int): Pair<Float, Float> {
    val last = (count - 1).coerceAtLeast(0).toFloat()
    val clamped = position.coerceIn(0f, last)
    val base = floor(clamped).toInt()
    val frac = clamped - base
    return if (frac <= .5f) base.toFloat() to (base + frac * 2f).coerceAtMost(last)
    else (base + (frac - .5f) * 2f).coerceAtMost(last) to (base + 1f).coerceAtMost(last)
}

// Swipeable catalog carousel for the new-widget sheet (C17): 3D incoming tilt, card fade, touch-on-card bounce and worm dots.
@Composable
internal fun WidgetPreviewPager(pagerState: PagerState, previews: WidgetPreviews, targets: List<PreviewTarget>,
    enabled: Boolean, activeTag: (Int) -> String) {
    WarmWidgetPreviews(previews, targets)
    // Track our own anchorPage and switch it only once near rest so release-time settledPage jumps cannot snap 3D tilt flat.
    var anchorPage by remember(pagerState) { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.currentPageOffsetFraction }
            .collect { (page, offset) ->
                if (abs(offset) < WidgetPagerMotion.AnchorSettleEpsilon) anchorPage = page
            }
    }
    HorizontalPager(state = pagerState, userScrollEnabled = enabled,
        flingBehavior = PagerDefaults.flingBehavior(pagerState,
            snapAnimationSpec = spring(WidgetPagerMotion.SnapDamping, WidgetPagerMotion.SnapStiffness,
                visibilityThreshold = WidgetPagerMotion.SnapVisibilityThresholdPx)),
        modifier = Modifier.fillMaxWidth().testTag("widget-pager")) { page ->
        val cardTag = if (page == pagerState.currentPage) activeTag(page) else null
        WidgetPagerCard(pagerState, page, anchorPage, previews, targets[page], enabled, cardTag)
    }
    Spacer(Modifier.height(WidgetPagerMotion.DotGapAbove))
    WidgetPagerDots(pagerState)
    Spacer(Modifier.height(WidgetPagerMotion.DotGapBelow))
}

@Composable
private fun WidgetPagerCard(pagerState: PagerState, page: Int, anchorPage: Int, previews: WidgetPreviews, target: PreviewTarget,
    enabled: Boolean, activeTag: String?) {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.isScrollInProgress, enabled) {
        if (pagerState.isScrollInProgress || !enabled) pressed = false
    }
    val pressScale by animateFloatAsState(if (pressed) WidgetPagerMotion.TouchSinkScale else 1f,
        if (pressed) spring(WidgetPagerMotion.PressDamping, WidgetPagerMotion.PressStiffness)
        else spring(WidgetPagerMotion.ReleaseDamping, WidgetPagerMotion.ReleaseStiffness), label = "widget-card-press")
    val tagModifier = if (activeTag != null) Modifier.testTag(activeTag) else Modifier
    Box(Modifier.fillMaxWidth().padding(vertical = WidgetPagerMotion.VerticalBuffer), contentAlignment = Alignment.Center) {
        ScaledWidgetPreview(previews, target, 0.dp, imageModifier = Modifier
            .cardTouchPress(enabled) { pressed = it }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                val offset = pagerPageOffset(page, pagerState.currentPage, pagerState.currentPageOffsetFraction)
                rotationY = incomingPageTilt(page, anchorPage, offset)
                alpha = pageCardAlpha(offset)
                compositingStrategy = CompositingStrategy.ModulateAlpha
                cameraDistance = WidgetPagerMotion.CameraDistance * density
                transformOrigin = TransformOrigin.Center
            }
            .then(tagModifier))
    }
}

// Tracks presses that land directly on the card without consuming events so HorizontalPager still owns sideways swipes.
private fun Modifier.cardTouchPress(enabled: Boolean, onPressed: (Boolean) -> Unit): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val origin = down.position
        onPressed(true)
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed ||
                    (change.position - origin).getDistance() > viewConfiguration.touchSlop) break
            }
        } finally { onPressed(false) }
    }
}

@Composable
private fun WidgetPagerDots(pagerState: PagerState) {
    val count = pagerState.pageCount
    if (count <= 0) return
    val baseColor = MiuixTheme.colorScheme.onSurface
    val width = WidgetPagerMotion.DotPitch * (count - 1).coerceAtLeast(0) + WidgetPagerMotion.DotSize
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width, WidgetPagerMotion.DotSize).testTag("widget-pager-dots")) {
            val dotPx = WidgetPagerMotion.DotSize.toPx()
            val radius = dotPx / 2f
            val pitchPx = WidgetPagerMotion.DotPitch.toPx()
            val inactiveColor = baseColor.copy(alpha = WidgetPagerMotion.DotInactiveAlpha)
            for (index in 0 until count) {
                drawCircle(inactiveColor, radius, Offset(radius + index * pitchPx, radius))
            }
            val (start, end) = wormSpan(pagerState.currentPage + pagerState.currentPageOffsetFraction, count)
            val left = start * pitchPx
            val right = end * pitchPx + dotPx
            drawRoundRect(baseColor.copy(alpha = WidgetPagerMotion.DotActiveAlpha), Offset(left, 0f),
                Size(right - left, dotPx), CornerRadius(radius, radius))
        }
    }
}
