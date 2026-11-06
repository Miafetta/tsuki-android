package cafe.miafetta.tsuki.ui.state

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.storageCall
import cafe.miafetta.tsuki.core.SnapshotJson
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.widget.WidgetRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WidgetPreviewSource(private val context: Context) {
    // [game] is a Branding game ID; it picks the widget the preview draws.
    suspend fun render(snapshot: WidgetSnapshot?, dark: Boolean, game: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val result = context.storageCall(Storage.PREVIEW, Bundle().apply {
            putString(Storage.SNAPSHOT, snapshot?.let { SnapshotJson.write(it).toString() })
            putString(Storage.THEME, if (dark) "DARK" else "LIGHT")
            putString(Storage.FEATURE_ID, widgetFeature(game))
        })
        result.getParcelable(Storage.BITMAP, android.graphics.Bitmap::class.java)?.asImageBitmap()
    }
}

// The widget feature of a game tab.
internal fun widgetFeature(game: String) = if (game == Branding.ENDFIELD_ID) WidgetRuntime.ENDFIELD_FEATURE else WidgetRuntime.FEATURE
