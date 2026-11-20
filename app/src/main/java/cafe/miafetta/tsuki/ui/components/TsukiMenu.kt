package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

/** A dropdown field's visible trigger; the popup draws a copy of [label] above its panel while it fades out. */
private class MenuTrigger(val label: @Composable () -> Unit, val onCovered: (Boolean) -> Unit)

@Composable
internal fun TsukiMenu(show: Boolean, items: List<DropdownItem>, onDismiss: () -> Unit,
    rightAligned: Boolean = true, destructiveIndex: Int = -1, dividerBefore: Int = -1) =
    TsukiMenuImpl(show, items, onDismiss, rightAligned, destructiveIndex, dividerBefore, null)

@Composable
private fun TsukiMenuImpl(show: Boolean, items: List<DropdownItem>, onDismiss: () -> Unit, rightAligned: Boolean,
    destructiveIndex: Int, dividerBefore: Int, trigger: MenuTrigger?) {
    var selected by remember { mutableStateOf(false) }
    LaunchedEffect(show) { if (show) selected = false }
    TsukiMenuHost(show, onDismiss, rightAligned, trigger) {
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

/**
 * A menu that grows out of a text trigger, such as a dropdown preference value. The trigger fades out above the
 * opening panel and fades back in after the panel has gone.
 */
@Composable
internal fun TsukiFieldMenu(show: Boolean, items: List<DropdownItem>, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    rightAligned: Boolean = true, label: @Composable () -> Unit) {
    val alpha = remember { Animatable(1f) }
    var covered by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (!show) alpha.animateTo(1f, tween(FieldTriggerFadeInMillis, FieldMenuCloseMillis, LinearEasing))
    }
    LaunchedEffect(show, covered) { if (show && covered) alpha.snapTo(0f) }
    Box(modifier) {
        Box(Modifier.graphicsLayer { this.alpha = alpha.value }) { label() }
        TsukiMenuImpl(show, items, onDismiss, rightAligned, -1, -1, MenuTrigger(label) { covered = it })
    }
}

/**
 * An icon button that opens a [TsukiMenu], for every icon-triggered menu. While the menu is open the button keeps no
 * press indication of its own, and the icon fades out as the menu opens over it and back in once the menu has gone,
 * with the timing of a dropdown field's value text (TsukiFieldMenu), so both kinds of trigger behave alike.
 */
@Composable
internal fun TsukiIconMenu(icon: ImageVector, contentDescription: String, items: List<DropdownItem>,
    modifier: Modifier = Modifier, enabled: Boolean = true, rightAligned: Boolean = true, destructiveIndex: Int = -1,
    dividerBefore: Int = -1) {
    var open by remember { mutableStateOf(false) }
    val show = open && enabled
    val alpha by menuTriggerAlpha(show)
    Box(modifier) {
        CompositionLocalProvider(LocalIndication provides (if (open) MiuixIndication(Color.Transparent) else LocalIndication.current)) {
            IconButton(onClick = { open = true }, Modifier.graphicsLayer { this.alpha = alpha }, enabled = enabled) {
                Icon(icon, contentDescription)
            }
        }
        TsukiMenu(show, items, { open = false }, rightAligned, destructiveIndex, dividerBefore)
    }
}

@Composable
private fun menuTriggerAlpha(show: Boolean): State<Float> {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(show) {
        if (show) alpha.animateTo(0f, tween(FieldTriggerFadeOutMillis, easing = LinearEasing))
        else alpha.animateTo(1f, tween(FieldTriggerFadeInMillis, MenuCloseMillis, LinearEasing))
    }
    return alpha.asState()
}

private class MenuFrame(val progress: Float, val fade: Float, val content: Float, val closing: Boolean)

@Composable
private fun menuFrame(transition: Transition<Boolean>, field: Boolean): MenuFrame {
    val progress by transition.animateFloat({
        when {
            !targetState -> tween(if (field) FieldMenuCollapseMillis else MenuCloseMillis, easing = MenuCloseEasing)
            else -> tween(if (field) FieldMenuOpenMillis else MenuOpenMillis, easing = LinearEasing)
        }
    }, label = "expansion") { if (it) 1f else 0f }
    // Fields fade out over the whole close; icon-button menus only fade during the first and last frames.
    val fade by transition.animateFloat({
        if (targetState || !field) snap() else tween(FieldMenuCloseMillis, easing = LinearEasing)
    }, label = "fade") { if (it) 1f else 0f }
    val content by transition.animateFloat({
        when {
            !targetState -> tween(65, easing = LinearEasing)
            field -> tween(FieldContentInMillis, easing = LinearEasing)
            else -> tween(110, delayMillis = 40, easing = LinearEasing)
        }
    }, label = "content") { if (it) 1f else 0f }
    val alpha = (progress / .08f).coerceIn(0f, 1f)
    return MenuFrame(progress, if (field) minOf(alpha, fade) else alpha, content, !transition.targetState)
}

@Composable
private fun TsukiMenuHost(show: Boolean, onDismiss: () -> Unit, rightAligned: Boolean, trigger: MenuTrigger?,
    content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = show
    val transition = rememberTransition(visible, label = "menu")
    val frame = menuFrame(transition, trigger != null)
    if (!visible.currentState && !visible.targetState && visible.isIdle) return

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val insets = IntRect(safe.getLeft(density, direction), safe.getTop(density), safe.getRight(density, direction), safe.getBottom(density))
    val window = LocalWindowInfo.current.containerSize
    val shadowPad = 24.dp
    val pad = with(density) { shadowPad.roundToPx() }
    val overlap = if (trigger == null) -1 else with(density) { FieldMenuOverlap.roundToPx() }
    var above by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    val position = remember(rightAligned, insets, pad, overlap) {
        TsukiMenuPosition(rightAligned, insets, pad, overlap, { anchor = it }) { above = it }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        Box(Modifier.sizeIn(
            maxWidth = with(density) { (window.width - insets.left - insets.right).coerceAtLeast(0).toDp() },
            maxHeight = with(density) { (window.height - insets.top - insets.bottom).coerceAtLeast(0).toDp() })
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }.padding(shadowPad)) {
            MenuPanel(frame, rightAligned, above, trigger != null, content)
            val bounds = anchor
            if (trigger != null && bounds != null) TriggerCopy(trigger, frame, bounds, pad)
        }
    }
}

@Composable
private fun BoxScope.TriggerCopy(trigger: MenuTrigger, frame: MenuFrame, bounds: IntRect, pad: Int) {
    DisposableEffect(Unit) { onDispose { trigger.onCovered(false) } }
    val alpha = if (frame.closing) 0f else (1f - frame.progress * FieldMenuOpenMillis / FieldTriggerFadeOutMillis).coerceIn(0f, 1f)
    // Placed absolutely at the trigger's window bounds, so RTL mirroring of the popup box does not move it.
    Box(Modifier.matchParentSize().layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints.fixed(bounds.width, bounds.height))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(bounds.left - pad, bounds.top - pad) }
    }.graphicsLayer { this.alpha = alpha }.clearAndSetSemantics {}
        // The row's own label hides only once this copy is on screen in the popup window.
        .drawWithContent {
            drawContent()
            trigger.onCovered(true)
        }) { trigger.label() }
}

@Composable
private fun MenuPanel(frame: MenuFrame, rightAligned: Boolean, above: Boolean, field: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var panelHeight by remember { mutableIntStateOf(0) }
    val motion = if (!field) menuMotion(frame.progress) else fieldMenuMotion(frame.progress,
        if (panelHeight > 0) with(density) { FieldPillHeight.toPx() } / panelHeight else .26f)
    val offset = if (field && frame.closing) 1f - frame.progress else motion.offset
    val shape = remember(motion, field) { MenuShape(motion, if (field) FieldStartWidth else .24f) }
    val dark = isTsukiDarkTheme()
    Box(Modifier.onSizeChanged { panelHeight = it.height }.graphicsLayer {
        // Offscreen alpha clips the soft shadow to a rectangle during the first/last frames.
        compositingStrategy = CompositingStrategy.ModulateAlpha
        scaleX = motion.width
        scaleY = motion.height
        alpha = frame.fade
        translationX = (if (rightAligned) -1 else 1) * (if (field) FieldMenuDrift else 20.dp).toPx() * offset
        translationY = (if (above) -1 else 1) * (if (field) FieldMenuDrift else 7.dp).toPx() * offset
        transformOrigin = TransformOrigin(if (rightAligned) 1f else 0f, if (above) 1f else 0f)
    }.dropShadow(shape, Shadow(radius = 32.dp, color = Color.Black, alpha = if (dark) .5f else .12f, offset = DpOffset(0.dp, 8.dp)))
        .clip(shape).background(MiuixTheme.colorScheme.surfaceContainer)
        .pointerInput(Unit) { detectTapGestures {} }
        .semantics { paneTitle = "菜单" }.selectableGroup()) {
        Box(Modifier.graphicsLayer {
            // Button menus scale text with the panel height, fields with its width; never with the surface stretch.
            if (field) scaleY = motion.width / motion.height else scaleX = motion.height / motion.width
            transformOrigin = TransformOrigin(0f, if (above && !field) 1f else 0f)
            alpha = frame.content
            val radius = menuContentBlurRadius(frame.content).dp.toPx()
            renderEffect = if (radius > .1f) android.graphics.RenderEffect.createBlurEffect(
                radius, radius, android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect() else null
        }) { content() }
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
    val show = expanded && enabled
    // The row stays held while the menu is open and releases with the value fade-in; Miuix's own release
    // spring lasts about 0.35 s, so the press indication is cleared and this overlay takes over.
    val hold = remember { Animatable(0f) }
    LaunchedEffect(show) {
        if (show) hold.snapTo(FieldHoldAlpha)
        else hold.animateTo(0f, tween(FieldTriggerFadeInMillis, FieldMenuCloseMillis, LinearEasing))
    }
    val holdColor = MiuixTheme.colorScheme.onBackground
    val color = MiuixTheme.colorScheme.onSurfaceVariantActions
    val value = items.getOrNull(selectedIndex) ?: "未知"
    Box(Modifier.testTag("dropdown:$title").drawWithContent {
        drawContent()
        if (hold.value > 0f) drawRect(holdColor, alpha = hold.value)
    }) {
        CompositionLocalProvider(LocalIndication provides (if (expanded) MiuixIndication(Color.Transparent) else LocalIndication.current)) {
        BasicComponent(title = title, insideMargin = LocalRowMargin.current, enabled = enabled, role = Role.DropdownList,
            onClick = { expanded = true }, endActions = {
                TsukiFieldMenu(show, items.mapIndexed { index, text ->
                    DropdownItem(text, selected = index == selectedIndex, onClick = { onSelectedIndexChange(index) })
                }, { expanded = false }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(value, Modifier.weight(1f, fill = false).padding(end = 8.dp),
                            style = MiuixTheme.textStyles.body2, color = color)
                        DropdownArrowEndAction(color)
                    }
                }
            })
        }
    }
}
