package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.state.UiRole

import cafe.miafetta.tsuki.ui.theme.AppLogo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Stored role labels keep the legacy " · " field delimiter; it is parsed here and never displayed.
internal val UiRole.server: String get() = label.substringAfter(" · ", "未知").substringBefore(" · ")

@Composable
internal fun RoleCard(roles: List<UiRole>, selected: UiRole, syncText: String, failed: Boolean, onSelect: (UiRole) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("role-card")) {
        Row(Modifier.fillMaxWidth().padding(BasicComponentDefaults.InsideMargin), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppLogo(size = 40)
            Column(Modifier.weight(1f)) {
                Box {
                    // The overlapping menu's rounded corner otherwise exposes the first glyph.
                    Row(Modifier.alpha(if (menu) 0f else 1f).clickable(enabled = roles.size > 1, role = Role.DropdownList) { menu = true }
                        .testTag("role-selector"), verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.name, Modifier.weight(1f, fill = false), style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (roles.size > 1) Icon(MiuixIcons.Basic.ArrowUpDown, "切换角色", Modifier.padding(start = 4.dp).size(10.dp, 16.dp))
                    }
                    TsukiMenu(menu, roles.map { role -> DropdownItem(role.name, summary = "${role.accountName}，${role.server}",
                        selected = role.key == selected.key, onClick = { onSelect(role) }) }, { menu = false }, rightAligned = false)
                }
                Text("${selected.server} UID ${selected.id}", style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SyncTag(syncText, failed)
        }
    }
}
