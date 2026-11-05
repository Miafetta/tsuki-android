package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.WidgetMetric
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MetricCard(label: String, metric: WidgetMetric?, detail: String, clock: String) {
    val light = !isTsukiDarkTheme()
    val sanity = label == "理智"
    Card(Modifier.fillMaxWidth().testTag("metric-card:$label")) {
        Column(Modifier.padding(BasicComponentDefaults.InsideMargin), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MiuixTheme.textStyles.body2)
            Text(buildAnnotatedString {
                append(metric?.text?.removePrefix("≈") ?: metric?.value?.toString() ?: "未知")
                metric?.maximum?.let { withStyle(MiuixTheme.textStyles.title3.toSpanStyle()
                    .copy(color = MiuixTheme.colorScheme.onSurfaceVariantSummary)) { append("/$it") } }
            }, style = MiuixTheme.textStyles.title1, fontSize = if (sanity) 34.sp else 28.sp,
                fontWeight = FontWeight.Bold)
            }
                Image(painterResource(if (sanity) { if (light) R.drawable.widget_sanity_light else R.drawable.widget_sanity_dark }
                    else { if (light) R.drawable.widget_drone_light else R.drawable.widget_drone_dark }), null, Modifier.size(44.dp))
            }
            LinearProgressIndicator(progress = metric?.maximum?.takeIf { it > 0 }?.let { (metric.value.toFloat() / it)
                .coerceIn(0f, 1f) } ?: 0f)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(detail, Modifier.testTag("metric-detail:$label").padding(end = 12.dp), style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(clock, style = MiuixTheme.textStyles.body2)
            }
        }
    }
}
