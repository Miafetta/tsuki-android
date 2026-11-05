package cafe.miafetta.tsuki.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleClip

// Display names shared by every page; the desktop widget labels live in strings.xml because the launcher reads them.
internal object Branding {
    const val GAME_ID = "arknights"
    const val GAME = "明日方舟"
    const val PLATFORM = "森空岛"
}

// Widget palettes in display order: stored value to label.
internal val widgetThemes = listOf("LIGHT" to "浅色", "DARK" to "深色")

// The same name the launcher's widget list shows, for example "明日方舟 浅色".
@Composable
internal fun widgetName(dark: Boolean): String =
    stringResource(if (dark) R.string.status_widget_dark_name else R.string.status_widget_name)

@Composable
internal fun AppLogo(platform: Boolean = false, size: Int = 34) {
    Image(painterResource(if (platform) R.drawable.logo_skland else R.drawable.logo_arknights),
        null, Modifier.size(size.dp).squircleClip((size * 0.3f).dp))
}
