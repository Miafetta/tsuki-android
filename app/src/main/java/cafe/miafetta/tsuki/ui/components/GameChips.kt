package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.glass.ChipShadowAnchor
import cafe.miafetta.tsuki.ui.glass.GlassBackground
import cafe.miafetta.tsuki.ui.glass.GlassSurface
import cafe.miafetta.tsuki.ui.glass.LocalGlass
import cafe.miafetta.tsuki.ui.glass.chipRowShadow
import cafe.miafetta.tsuki.ui.glass.chipSelectionColors
import cafe.miafetta.tsuki.ui.glass.chipShadowAnchor
import cafe.miafetta.tsuki.ui.glass.nativeGlassEnabled

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GameChips(games: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, progress: () -> Float = { 0f }) {
    // The row draws chip shadows outside the scroll container's clipping overscroll layer (chipRowShadow); the chips tell it
    // where they are.
    val shadowAnchor = remember { ChipShadowAnchor() }
    val glass = LocalGlass.current
    val unselectedGlass = glass != null && glass.enabled && (glass.available || nativeGlassEnabled(glass))
    Row(modifier.fillMaxWidth().chipRowShadow(shadowAnchor, selectedId, unselectedGlass, progress)
        .horizontalScroll(rememberScrollState())
        .selectableGroup().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp).testTag("game-tabs"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        games.forEach { (id, label) ->
            val selected = selectedId == id
            val shape = RoundedCornerShape(50)
            val selectionColors = chipSelectionColors()
            Box(Modifier.chipShadowAnchor(shadowAnchor, id)) {
                GlassBackground(GlassSurface.Chip, Modifier.matchParentSize(), progress(), selected)
                Box(Modifier.height(38.dp).widthIn(min = 86.dp).clip(shape)
                    .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(id) })
                    .padding(horizontal = 22.dp).testTag("game-chip:$id"), contentAlignment = Alignment.Center) {
                    // 14sp like the system theme store chips. HyperOS draws Medium as heavy as Bold, so the unselected
                    // label uses Normal, which renders like the system's unselected chip; subtitle's own Bold is overridden.
                    Text(label, style = MiuixTheme.textStyles.subtitle,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) selectionColors.second else MiuixTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
internal fun GameTabs(games: List<Pair<String, String>>, selectedGameId: String, onGameSelect: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, progress: () -> Float = { 0f }) = GameChips(games, selectedGameId,
        onGameSelect, modifier, enabled, progress)
