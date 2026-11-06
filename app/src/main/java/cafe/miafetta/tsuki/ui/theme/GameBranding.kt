package cafe.miafetta.tsuki.ui.theme

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleClip

// Display names shared by every page; the desktop widget labels live in strings.xml because the launcher reads them.
internal object Branding {
    const val GAME_ID = "arknights"
    const val GAME = "明日方舟"
    const val ENDFIELD_ID = "endfield"
    // Short name for tabs and summaries; the widget names in strings.xml use the full title.
    const val ENDFIELD = "终末地"
    const val PLATFORM = "森空岛"
    // Game tabs in display order: game ID to name.
    val games = listOf(GAME_ID to GAME, ENDFIELD_ID to ENDFIELD)

    fun gameName(id: String) = games.firstOrNull { it.first == id }?.second ?: GAME
}

// Widget palettes in display order: stored value to label.
internal val widgetThemes = listOf("LIGHT" to "浅色", "DARK" to "深色")

// The same name the launcher's widget list shows, for example "明日方舟数据工具 浅色". The Endfield card has one palette.
@Composable
internal fun widgetName(dark: Boolean, game: String = Branding.GAME_ID): String = stringResource(when {
    game == Branding.ENDFIELD_ID -> R.string.endfield_widget_name
    dark -> R.string.status_widget_dark_name
    else -> R.string.status_widget_name
})

// Every widget in catalog order: game ID, stored palette. Only Arknights offers a light and a dark card.
internal val widgetCatalog = listOf(Branding.GAME_ID to "LIGHT", Branding.GAME_ID to "DARK", Branding.ENDFIELD_ID to "DARK")

@Composable
internal fun AppLogo(platform: Boolean = false, size: Int = 34, game: String = Branding.GAME_ID) {
    val resources = LocalResources.current
    val id = when {
        platform -> R.drawable.logo_skland
        game == Branding.ENDFIELD_ID -> R.drawable.logo_endfield
        else -> R.drawable.logo_arknights
    }
    Image(remember(id) { BrandLogos.get(resources, id) }, null, Modifier.size(size.dp).squircleClip((size * 0.3f).dp))
}

// The logos are 512px JPEGs shown at 34–64dp. painterResource decoded them on the main thread for every new call site,
// so each page that composed a logo paid a full decode. Decode each once per process at 256px (enough for 64dp at 4x)
// and share it; TsukiApp preloads both off the main thread and uploads them to the GPU ahead of the first draw.
internal object BrandLogos {
    private val images = ConcurrentHashMap<Int, ImageBitmap>()

    fun get(resources: Resources, id: Int): ImageBitmap = images.getOrPut(id) {
        BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inSampleSize = 2 })
            .apply { prepareToDraw() }.asImageBitmap()
    }

    private val preloading = AtomicBoolean(false)

    // Called from the first composition, before any logo is composed. It starts at most once per process (later calls
    // only read the flag), and a plain thread keeps coroutine creation out of composition.
    fun preloadAsync(resources: Resources) {
        if (!preloading.compareAndSet(false, true)) return
        thread(name = "tsuki-logos") {
            get(resources, R.drawable.logo_skland)
            get(resources, R.drawable.logo_arknights)
            get(resources, R.drawable.logo_endfield)
        }
    }
}
