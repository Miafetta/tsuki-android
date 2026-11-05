package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.theme.Branding

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope

@Composable
internal fun PlatformSelector(busy: Boolean, blurEnabled: Boolean = true, onBack: () -> Unit) {
    val transition = LocalNavTransitionScope.current
    val active by remember(transition) { derivedStateOf { !transition.isRunning && transition.relativeDepth == 0f } }
    PredictiveBackHandler(enabled = busy, onProgress = { events -> events.collect {} }, onCommit = {}, onCancel = {})
    SecondaryScaffold("选择平台", blurEnabled, active, onBack, backEnabled = active && !busy,
        modifier = Modifier.testTag("platform-selector-page"), contentPadding = PaddingValues(horizontal = 12.dp)) {
        TsukiSmallTitle("平台")
        Card {
            RadioButtonPreference(title = Branding.PLATFORM, selected = true, enabled = active && !busy, onClick = onBack,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    selected = true
                    if (busy || !active) disabled()
                })
        }
    }
}
