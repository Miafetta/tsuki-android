package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

// The tinted backdrop behind a widget preview; production code uses it through WidgetPreviewStage.
@Composable
internal fun WidgetStage(modifier: Modifier = Modifier, preview: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.linearGradient(listOf(lerp(colors.surface, colors.primary, .28f), lerp(colors.surface, colors.primary, .07f))))
        .padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        preview()
    }
}
