package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SyncTag(text: String, failed: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    Text(text, Modifier.widthIn(max = 120.dp).background(if (failed) colors.errorContainer else colors.secondaryContainer,
        RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
        fontSize = 12.sp, color = if (failed) colors.error else colors.onSurfaceVariantSummary,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}
