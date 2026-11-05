package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.WidgetTraining

// The loader comes from the state layer; without one, or until it returns, the placeholder artwork is shown.
@Composable
internal fun TrainingPortrait(training: WidgetTraining, fetchedAt: Long,
    load: (suspend (WidgetTraining, Boolean) -> ImageBitmap?)?, modifier: Modifier) {
    val light = !isTsukiDarkTheme()
    val portrait by produceState<ImageBitmap?>(null, training.avatarUrl, training.traineeId, fetchedAt, light) {
        value = load?.invoke(training, light)
    }
    val description = "训练室干员 ${training.name ?: "名称未知"}，${training.status(System.currentTimeMillis())}"
    if (portrait != null) Image(portrait!!, description, modifier)
    else Image(painterResource(if (light) R.drawable.widget_training_light else R.drawable.widget_training_dark), description, modifier)
}
