package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.core.WidgetMetric
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MetricRow(label: String, metric: WidgetMetric?, detail: String?) {
    val colors = MiuixTheme.colorScheme
    val refresh = label == "公招刷新"
    BasicComponent(title = label, summary = detail, modifier = Modifier.testTag("metric-card:$label"), startAction = {
        if (label != "干员疲劳") ProgressRing(metric?.maximum?.takeIf { it > 0 }?.let { metric.value.toFloat() / it } ?: 0f)
        else Spacer(Modifier.size(24.dp))
    }, endActions = {
        Text(buildAnnotatedString {
            append(if (refresh && metric != null) { if (metric.value > 0) "可刷新" else "联络中" }
                else metric?.text?.removePrefix("≈") ?: metric?.value?.toString() ?: "未知")
            if (!refresh) metric?.maximum?.let { withStyle(MiuixTheme.textStyles.body2.toSpanStyle()
                .copy(color = colors.onSurfaceVariantSummary)) { append("/$it") } }
        }, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium,
            color = when { label == "干员疲劳" && (metric?.value ?: 0) > 0 -> colors.error
                refresh && (metric?.value ?: 0) > 0 -> colors.primary
                else -> colors.onSurface })
    })
}
