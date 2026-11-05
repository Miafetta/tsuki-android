package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun TsukiSmallTitle(text: String, modifier: Modifier = Modifier) =
    SmallTitle(text, modifier, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
