package cafe.miafetta.tsuki.ui.state

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.storageCall
import cafe.miafetta.tsuki.core.SnapshotJson
import cafe.miafetta.tsuki.core.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WidgetPreviewSource(private val context: Context) {
    suspend fun render(snapshot: WidgetSnapshot?, dark: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        val result = context.storageCall(Storage.PREVIEW, Bundle().apply {
            putString(Storage.SNAPSHOT, snapshot?.let { SnapshotJson.write(it).toString() })
            putString(Storage.THEME, if (dark) "DARK" else "LIGHT")
        })
        result.getParcelable(Storage.BITMAP, android.graphics.Bitmap::class.java)?.asImageBitmap()
    }
}
