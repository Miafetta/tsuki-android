package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Canvas(modifier.size(24.dp)) {
        val stroke = 3.dp.toPx()
        val inset = Offset(stroke / 2, stroke / 2)
        val ringSize = Size(size.width - stroke, size.height - stroke)
        drawArc(colors.secondaryContainer, -90f, 360f, false, inset, ringSize, style = Stroke(stroke))
        drawArc(colors.primary, -90f, progress.coerceIn(0f, 1f) * 360f, false, inset, ringSize,
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
