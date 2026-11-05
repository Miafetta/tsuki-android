package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.AppLogo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun EmptyGuide(onAccount: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppLogo(platform = true, size = 64)
            Text("还没有游戏角色", style = MiuixTheme.textStyles.title4)
            Button(onClick = onAccount, colors = ButtonDefaults.buttonColorsPrimary()) { Text("添加森空岛账号") }
        }
    }
}
