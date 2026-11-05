package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TrainingCard(name: String, detail: String, portrait: @Composable (Modifier) -> Unit) {
    val style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold)
    val height = with(LocalDensity.current) { rememberTextMeasurer().measure("国", style, maxLines = 1).size.height.toDp() }
    Card(Modifier.fillMaxWidth().testTag("metric-card:训练室")) {
        Row(Modifier.padding(BasicComponentDefaults.InsideMargin), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("训练室", style = MiuixTheme.textStyles.body2)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(height).testTag("training-name-slot"), contentAlignment = Alignment.CenterStart) {
                    Text(name, Modifier.fillMaxWidth(), style = style,
                        autoSize = TextAutoSize.StepBased(MiuixTheme.textStyles.body2.fontSize, style.fontSize, 1.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Text(detail, Modifier.testTag("metric-detail:训练室"), style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            portrait(Modifier.size(48.dp).testTag("training-portrait"))
        }
    }
}
