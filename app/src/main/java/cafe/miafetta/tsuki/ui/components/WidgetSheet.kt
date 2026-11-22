package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.AppLogo
import cafe.miafetta.tsuki.ui.theme.Branding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Measured on HyperOS's add and edit widget sheets (2026-11-20, 3.25 px/dp). The settings text and the main button start
// 25dp from the sheet edge ([WidgetSheetInset]), and a pressed settings row is highlighted across the whole sheet. So the
// sheet has no inside margin (Miuix's default is 24dp): the settings rows span it and keep the 25dp inside themselves
// (LocalRowMargin), and the rest of its content either takes [WidgetSheetInset] or is centred. The back or close icon
// is centred 37dp from the edge (17dp and half of the 40dp button).
internal val WidgetSheetInset = 25.dp
private val DismissInset = 17.dp
// The system sheet keeps its full height whatever it holds and stops about 10dp below the status bar.
private val TopGap = 10.dp
// The preview starts 110dp below the title's centre line (edit) or 109dp below the description's (new); the settings
// start 84dp below the preview.
private val PreviewGapAfterTitle = 78.dp
private val PreviewGapAfterIntro = 100.dp
private val PreviewGapBelow = 84.dp
// The game's logo is 18dp, 8dp before its name.
private const val LogoSize = 18
private val LogoGap = 8.dp
private val ErrorGap = 8.dp
private val BottomGap = 16.dp

// The sheet that adds or edits a widget, after HyperOS's own: [header] on top, then [preview] at the width the Widgets page
// gives it, [settings] laid straight on the sheet and across its full width (anything else in them takes
// [WidgetSheetInset]), and at the bottom [error] above one full-width main button. The button is disabled while [busy] or
// unless [canConfirm]; while busy the sheet cannot be dismissed either. Without a [preview] the settings take its place
// (an empty state, for example). Background per C8.
@Composable
internal fun WidgetSheet(show: Boolean, header: WidgetSheetHeader, previews: WidgetPreviews, preview: PreviewTarget?,
    busy: Boolean, canConfirm: Boolean, error: String, onDismiss: () -> Unit, onConfirm: () -> Unit,
    modifier: Modifier = Modifier, onDismissFinished: (() -> Unit)? = null, previewModifier: Modifier = Modifier,
    headerAlpha: Float = 1f, settingsAlpha: Float = 1f, previewContent: (@Composable () -> Unit)? = null,
    settings: @Composable ColumnScope.() -> Unit) {
    OverlayBottomSheet(show = show, modifier = modifier, title = header.title, backgroundColor = MiuixTheme.colorScheme.surface,
        sheetMaxWidth = 600.dp, insideMargin = DpSize(0.dp, 0.dp),
        startAction = { SheetStart(header, busy, onDismiss, headerAlpha) },
        onDismissRequest = { if (!busy) onDismiss() }, onDismissFinished = onDismissFinished, allowDismiss = !busy) {
        Column(Modifier.fillMaxWidth().fillHeightLess(TopGap).navigationBarsPadding().padding(bottom = BottomGap)) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                if (header is WidgetSheetHeader.New) Intro(header, headerAlpha)
                if (previewContent != null) {
                    val baseGap = if (header is WidgetSheetHeader.New) PreviewGapAfterIntro else PreviewGapAfterTitle
                    Spacer(Modifier.height((baseGap - WidgetPagerMotion.VerticalBuffer).coerceAtLeast(0.dp)))
                    previewContent()
                } else {
                    Spacer(Modifier.height(if (header is WidgetSheetHeader.New) PreviewGapAfterIntro else PreviewGapAfterTitle))
                    if (preview != null) {
                        ScaledWidgetPreview(previews, preview, 0.dp, imageModifier = previewModifier)
                        Spacer(Modifier.height(PreviewGapBelow))
                    }
                }
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = settingsAlpha }) {
                    CompositionLocalProvider(LocalRowMargin provides sheetRowMargin()) { settings() }
                }
            }
            if (error.isNotEmpty()) Text(error, Modifier.fillMaxWidth().padding(horizontal = WidgetSheetInset, vertical = ErrorGap),
                color = MiuixTheme.colorScheme.error, textAlign = TextAlign.Center, style = MiuixTheme.textStyles.footnote1)
            TextButton(header.confirmLabel, onClick = { if (!busy && canConfirm) onConfirm() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = WidgetSheetInset).testTag("config-action"),
                enabled = canConfirm && !busy, colors = ButtonDefaults.textButtonColorsPrimary(),
                textStyle = MiuixTheme.textStyles.button.copy(fontWeight = FontWeight.Bold))
        }
    }
}

// The settings rows' margin: the sheet's text inset at the sides, BasicComponent's own above and below.
internal fun sheetRowMargin(): PaddingValues =
    PaddingValues(horizontal = WidgetSheetInset, vertical = BasicComponentDefaults.InsideMargin.calculateTopPadding())

// Takes all the height the sheet allows less [gap]; the sheet itself already stops below the status bar.
private fun Modifier.fillHeightLess(gap: Dp) = layout { measurable, constraints ->
    val height = (constraints.maxHeight - gap.roundToPx()).coerceAtLeast(constraints.minHeight)
    val placeable = measurable.measure(
        if (constraints.hasBoundedHeight) constraints.copy(minHeight = height, maxHeight = height) else constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

// The title row's start slot. The new head spans the row to centre the game in it, where the edit head has its title.
@Composable
private fun SheetStart(header: WidgetSheetHeader, busy: Boolean, onDismiss: () -> Unit, headerAlpha: Float) {
    if (header is WidgetSheetHeader.New) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart)) { DismissButton(header, busy, onDismiss) }
        Row(Modifier.graphicsLayer { alpha = headerAlpha }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogoGap)) {
            AppLogo(size = LogoSize, game = header.game)
            // The same type as the sheet title of the edit head (OverlayBottomSheet's title row).
            Text(Branding.gameName(header.game), color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Medium)
        }
    } else DismissButton(header, busy, onDismiss)
}

@Composable
private fun DismissButton(header: WidgetSheetHeader, busy: Boolean, onDismiss: () -> Unit) {
    IconButton(onClick = { if (!busy) onDismiss() }, modifier = Modifier.padding(start = DismissInset), enabled = !busy) {
        Icon(if (header is WidgetSheetHeader.New) MiuixIcons.ChevronBackward else MiuixIcons.Close, header.dismissLabel)
    }
}

// The widget's name in large bold type with its one-line description below, both centred.
@Composable
private fun Intro(header: WidgetSheetHeader.New, alpha: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = WidgetSheetInset).graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(header.name, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(header.description, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center)
    }
}
