package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// HyperOS 4 Settings' section titles (更多设置, 2026-11-20, 3.25 px/dp): bold, a CJK advance of 40 px (12.3sp), in a
// blue grey close to Miuix's onBackgroundVariant; the ink starts 21.1dp below the card above and ends 10.1dp above the
// card below, and a page that opens with a title has its ink 9.6dp below where the first content starts (topBarContentTop).
// The text starts where the cards' row text starts.
internal val SmallTitleInkBelowContent = 21.1.dp
internal val SmallTitleInkAboveCard = 10.1.dp
internal val SmallTitleInkBelowStart = 9.6.dp
private val SmallTitleInset = 16.dp

// footnote1's line box on the reference phone, measured on Tsuki's own 12sp widget names (2026-11-20): the ink of CJK text
// starts 0.19 em below the top of the line and ends 0.21 em above its bottom. The title is placed by its ink.
internal const val LineAboveInkEm = .19f
internal const val LineBelowInkEm = .21f

// The spacing AppScaffold's lists put between items.
internal val PageListSpacing = 10.dp

// Space above the title's line box. [spacingAbove] is what the list or column around it already leaves between the
// content above and the title; a [first] title opens its page.
internal fun smallTitleTop(em: Dp, spacingAbove: Dp, first: Boolean): Dp =
    ((if (first) SmallTitleInkBelowStart else SmallTitleInkBelowContent - spacingAbove) - em * LineAboveInkEm)
        .coerceAtLeast(0.dp)

// Space below the line box, down to the card. [spacingBelow] is what the list leaves when the card is its next item;
// the result is negative when that is more than the gap under the ink, and the card then starts inside the line box.
internal fun smallTitleBottom(em: Dp, spacingBelow: Dp): Dp = SmallTitleInkAboveCard - em * LineBelowInkEm - spacingBelow

// A section title, sitting straight above its card (or field). Miuix's SmallTitle draws subtitle (14sp) and takes no
// style, so this is a Miuix Text in SmallTitle's colour, at footnote1's size (12sp, the nearest text style) in bold.
// Place it with what the layout already adds: [spacingAbove] (PageListSpacing on the main pages), [spacingBelow] when its
// card is the next list item, [first] when it opens the page.
@Composable
internal fun TsukiSmallTitle(text: String, modifier: Modifier = Modifier, spacingAbove: Dp = 0.dp, spacingBelow: Dp = 0.dp,
    first: Boolean = false) {
    val style = MiuixTheme.textStyles.footnote1
    val em = with(LocalDensity.current) { style.fontSize.toDp() }
    Text(text, modifier.titleSpace(smallTitleTop(em, spacingAbove, first), smallTitleBottom(em, spacingBelow))
        .padding(horizontal = SmallTitleInset), color = MiuixTheme.colorScheme.onBackgroundVariant, style = style,
        fontWeight = FontWeight.Bold)
}

// Adds [top] above and [bottom] below; a negative [bottom] makes the title report less than its line box, so what
// follows starts below the ink but inside the line box.
private fun Modifier.titleSpace(top: Dp, bottom: Dp) = layout { measurable, constraints ->
    val topPx = top.roundToPx()
    val bottomPx = bottom.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = -(topPx + bottomPx.coerceAtLeast(0))))
    layout(placeable.width, (topPx + placeable.height + bottomPx).coerceAtLeast(0)) { placeable.place(0, topPx) }
}
