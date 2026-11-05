package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.glass.GlassBackground
import cafe.miafetta.tsuki.ui.glass.GlassSurface
import cafe.miafetta.tsuki.ui.glass.chipSelectionColors

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GameChips(games: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, progress: () -> Float = { 0f }) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp).testTag("game-tabs"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        games.forEach { (id, label) ->
            val selected = selectedId == id
            val shape = RoundedCornerShape(50)
            val selectionColors = chipSelectionColors()
            Box {
                GlassBackground(GlassSurface.Chip, Modifier.matchParentSize(), progress(), selected)
                Box(Modifier.height(38.dp).widthIn(min = 86.dp).clip(shape)
                    .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(id) })
                    .padding(horizontal = 22.dp).testTag("game-chip:$id"), contentAlignment = Alignment.Center) {
                    Text(label, style = MiuixTheme.textStyles.body1, fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
