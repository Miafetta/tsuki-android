package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

// Fitted to the original recording PTS
// these are not HyperOS spring constants.
internal fun menuContentBlurRadius(visibility: Float): Float =
    if (visibility <= 0f || visibility >= 1f) 0f else 6f * (1f - visibility)

internal const val MenuOpenMillis = 330
internal const val MenuCloseMillis = 170
private val MenuTimes = floatArrayOf(0f, .17f, .37f, .57f, .77f, 1f)
private val MenuWidths = floatArrayOf(.24f, .42f, .73f, .91f, .99f, 1f)
private val MenuHeights = floatArrayOf(.45f, .58f, .82f, .96f, 1f, 1f)
private val MenuOffsets = floatArrayOf(0f, .8f, 1f, .5f, .1f, 0f)
internal data class MenuMotion(val width: Float, val height: Float, val offset: Float)
internal fun menuMotion(progress: Float): MenuMotion {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val i = (MenuTimes.indexOfFirst { it >= p }.coerceAtLeast(1) - 1).coerceAtMost(MenuTimes.lastIndex - 1)
    val fraction = (p - MenuTimes[i]) / (MenuTimes[i + 1] - MenuTimes[i])
    fun sample(values: FloatArray) = values[i] + (values[i + 1] - values[i]) * fraction
    return MenuMotion(sample(MenuWidths), sample(MenuHeights), sample(MenuOffsets))
}

private class MenuShape(private val motion: MenuMotion) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // Keep circular corners while the panel expands at different rates on each axis.
        val radius = minOf(with(density) { (20f + 4f * (1f - motion.width) / .76f).dp.toPx() },
            size.width * motion.width / 2, size.height * motion.height / 2)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height,
            CornerRadius(radius / motion.width, radius / motion.height)))
    }
}

internal class TsukiMenuPosition(private val rightAligned: Boolean, private val safeInsets: IntRect = IntRect.Zero,
    private val shadowPad: Int = 0, private val onPosition: (Boolean) -> Unit = {}) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val bounds = IntRect(safeInsets.left, safeInsets.top, windowSize.width - safeInsets.right, windowSize.height - safeInsets.bottom)
        val panel = IntSize((popupContentSize.width - 2 * shadowPad).coerceAtLeast(0),
            (popupContentSize.height - 2 * shadowPad).coerceAtLeast(0))
        val above = anchorBounds.top + panel.height > bounds.bottom
        val x = if (rightAligned) anchorBounds.right - panel.width else anchorBounds.left
        val y = if (above) anchorBounds.bottom - panel.height else anchorBounds.top
        onPosition(above)
        return IntOffset(x.coerceIn(bounds.left, (bounds.right - panel.width).coerceAtLeast(bounds.left)) - shadowPad,
            y.coerceIn(bounds.top, (bounds.bottom - panel.height).coerceAtLeast(bounds.top)) - shadowPad)
    }
}

@Composable
internal fun TsukiMenu(show: Boolean, items: List<DropdownItem>, onDismiss: () -> Unit,
    rightAligned: Boolean = true, destructiveIndex: Int = -1, dividerBefore: Int = -1) {
    var selected by remember { mutableStateOf(false) }
    LaunchedEffect(show) { if (show) selected = false }
    TsukiMenuHost(show, onDismiss, rightAligned) {
        TsukiMenuContent(items.map { item ->
            item.copy(onClick = {
                if (show && !selected) {
                    selected = true
                    onDismiss()
                    item.onClick?.invoke()
                }
            })
        }, {}, destructiveIndex, dividerBefore)
    }
}

@Composable
private fun TsukiMenuHost(show: Boolean, onDismiss: () -> Unit, rightAligned: Boolean, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = show
    val transition = rememberTransition(visible, label = "menu")
    val progress by transition.animateFloat({ tween(if (targetState) MenuOpenMillis else MenuCloseMillis,
        easing = if (targetState) LinearEasing else CubicBezierEasing(0f, .5f, .4f, 1f))
    }, label = "expansion") { if (it) 1f else 0f }
    val contentVisibility by transition.animateFloat({
        if (targetState) tween(110, delayMillis = 40, easing = LinearEasing) else tween(65, easing = LinearEasing)
    }, label = "content") { if (it) 1f else 0f }
    if (!visible.currentState && !visible.targetState && visible.isIdle) return
    val motion = menuMotion(progress)

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val insets = IntRect(safe.getLeft(density, direction), safe.getTop(density), safe.getRight(density, direction), safe.getBottom(density))
    val window = LocalWindowInfo.current.containerSize
    val shadowPad = 24.dp
    val pad = with(density) { shadowPad.roundToPx() }
    var above by remember { mutableStateOf(false) }
    val position = remember(rightAligned, insets, pad) { TsukiMenuPosition(rightAligned, insets, pad) { above = it } }
    val shape = remember(motion) { MenuShape(motion) }
    val dark = isTsukiDarkTheme()
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        Box(Modifier.sizeIn(
            maxWidth = with(density) { (window.width - insets.left - insets.right).coerceAtLeast(0).toDp() },
            maxHeight = with(density) { (window.height - insets.top - insets.bottom).coerceAtLeast(0).toDp() })
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }.padding(shadowPad)) {
            Box(Modifier.graphicsLayer {
                // Offscreen alpha clips the soft shadow to a rectangle during the first/last frames.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                scaleX = motion.width
                scaleY = motion.height
                alpha = (progress / .08f).coerceIn(0f, 1f)
                translationX = (if (rightAligned) -20.dp.toPx() else 20.dp.toPx()) * motion.offset
                translationY = (if (above) -7.dp.toPx() else 7.dp.toPx()) * motion.offset
                transformOrigin = TransformOrigin(if (rightAligned) 1f else 0f, if (above) 1f else 0f)
            }.dropShadow(shape, Shadow(radius = 32.dp, color = Color.Black, alpha = if (dark) .5f else .12f, offset = DpOffset(0.dp, 8.dp)))
                .clip(shape).background(MiuixTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures {} }
                .semantics { paneTitle = "菜单" }.selectableGroup()) {
                Box(Modifier.graphicsLayer {
                    // Text scales uniformly, independently of the surface's horizontal expansion.
                    scaleX = motion.height / motion.width
                    transformOrigin = TransformOrigin(0f, if (above) 1f else 0f)
                    alpha = contentVisibility
                    val radius = menuContentBlurRadius(contentVisibility).dp.toPx()
                    renderEffect = if (radius > .1f) android.graphics.RenderEffect.createBlurEffect(
                        radius, radius, android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect() else null
                }) { content() }
            }
        }
    }
}

@Composable
internal fun TsukiMenuContent(items: List<DropdownItem>, onDismiss: () -> Unit = {},
    destructiveIndex: Int = -1, dividerBefore: Int = -1) {
    Box(Modifier.testTag("tsuki-menu").semantics { testTagsAsResourceId = true }) {
        ListPopupColumn {
            items.forEachIndexed { index, item ->
                if (index == dividerBefore) HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    color = MiuixTheme.colorScheme.dividerLine)
                DropdownImpl(item = item, optionSize = items.size, isSelected = item.selected, index = index,
                    dropdownColors = if (index == destructiveIndex)
                        DropdownDefaults.dropdownColors(contentColor = MiuixTheme.colorScheme.error)
                    else DropdownDefaults.dropdownColors(),
                    onSelectedIndexChange = {
                        item.onClick?.invoke()
                        onDismiss()
                    })
            }
        }
    }
}

@Composable
internal fun TsukiDropdownPreference(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.testTag("dropdown:$title")) {
        // Miuix Cancel still fades for 0.35 s
        // clear only the indication when the popup takes focus.
        CompositionLocalProvider(LocalIndication provides (if (expanded) MiuixIndication(Color.Transparent) else LocalIndication.current)) {
        BasicComponent(title = title, enabled = enabled, role = Role.DropdownList,
            onClick = { expanded = true }, endActions = {
                Text(items.getOrNull(selectedIndex) ?: "未知", Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                DropdownArrowEndAction(MiuixTheme.colorScheme.onSurfaceVariantActions)
            })
        }
        TsukiMenu(expanded && enabled, items.mapIndexed { index, text ->
            DropdownItem(text, selected = index == selectedIndex, onClick = { onSelectedIndexChange(index) })
        }, { expanded = false })
    }
}
