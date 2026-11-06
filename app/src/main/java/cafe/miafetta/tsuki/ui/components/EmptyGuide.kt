package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.AppLogo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun EmptyGuide(onAccount: () -> Unit, title: String = "还没有游戏角色", hint: String? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppLogo(platform = true, size = 64)
            Text(title, style = MiuixTheme.textStyles.title4, textAlign = TextAlign.Center)
            if (hint != null) Text(hint, style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Button(onClick = onAccount, colors = ButtonDefaults.buttonColorsPrimary()) { Text("添加森空岛账号") }
        }
    }
}
