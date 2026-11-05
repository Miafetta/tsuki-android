package cafe.miafetta.tsuki.ui.state

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.storageCall
import cafe.miafetta.tsuki.core.WidgetTraining
import cafe.miafetta.tsuki.providers.OfficialAvatars
import cafe.miafetta.tsuki.widget.decodeAvatar
import cafe.miafetta.tsuki.widget.roundedAvatar
import cafe.miafetta.tsuki.widget.trainingPortrait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Reads the avatar cached by the widget process and frames it the way the desktop widget does.
internal class TrainingPortraitSource(private val context: Context) {
    suspend fun load(training: WidgetTraining, light: Boolean): ImageBitmap? {
        val url = training.avatarUrl ?: training.traineeId?.let(OfficialAvatars::character) ?: return null
        return withContext(Dispatchers.IO) {
            context.storageCall(Storage.AVATAR, Bundle().apply { putString(Storage.URL, url) }).getByteArray(Storage.BYTES)
                ?.let(::decodeAvatar)?.let { trainingPortrait(context, roundedAvatar(it, 1f / 9.5f), light).asImageBitmap() }
        }
    }
}
