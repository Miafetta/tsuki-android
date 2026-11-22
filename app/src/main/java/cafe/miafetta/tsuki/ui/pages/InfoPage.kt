package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.glass.rememberNavActive

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// A numbered section of a formal information page; each row is a short heading with its explanation.
internal data class InfoSection(val title: String, val rows: List<Pair<String, String>>)

// Shared layout of the privacy notice and the open source notice: a document header, numbered sections, closing notes.
@Composable
internal fun InfoPage(title: String, onBack: () -> Unit, blurEnabled: Boolean, heading: String, meta: String, intro: String,
    sections: List<InfoSection>, notes: List<String> = emptyList(), extra: @Composable ColumnScope.() -> Unit = {}) {
    val active = rememberNavActive()
    SecondaryScaffold(title, blurEnabled, active, onBack, contentPadding = PaddingValues(horizontal = 12.dp)) {
        InfoHeader(heading, meta, intro)
        sections.forEachIndexed { index, section -> InfoSectionCard(index + 1, section) }
        extra()
        notes.forEach { note ->
            Text(note, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

// The page's first content: it starts where SecondaryScaffold puts every page's first content (topBarContentTop).
@Composable
private fun InfoHeader(heading: String, meta: String, intro: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(heading, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold)
            Text(meta, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(intro, Modifier.padding(top = 4.dp), style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun InfoSectionCard(number: Int, section: InfoSection) {
    TsukiSmallTitle("$number. ${section.title}")
    Card(Modifier.fillMaxWidth()) {
        section.rows.forEach { (name, summary) -> BasicComponent(title = name, summary = summary) }
    }
}
