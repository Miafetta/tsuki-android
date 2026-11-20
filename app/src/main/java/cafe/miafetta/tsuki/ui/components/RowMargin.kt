package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults

// The inside margin of Tsuki's setting rows (TsukiDropdownPreference). Rows on a card keep BasicComponent's default. A
// container whose rows span its full width, so that their press highlight does too, provides the margin that keeps
// their text where it belongs (WidgetSheet).
internal val LocalRowMargin = staticCompositionLocalOf<PaddingValues> { BasicComponentDefaults.InsideMargin }
