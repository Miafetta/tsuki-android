package cafe.miafetta.tsuki.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun BoxScope.NativeTopGlass(mask: Brush, progress: () -> Float, visible: Boolean, radius: Float, report: (Boolean) -> Unit) {
    val currentProgress by rememberUpdatedState(progress)
    Box(Modifier.matchParentSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(mask, alpha = currentProgress().coerceIn(0f, 1f), blendMode = BlendMode.DstIn)
        }) {
        AndroidView(modifier = Modifier.matchParentSize().layout { measurable, constraints ->
            // Move the native top, left and right edges outside the viewport (the outermost column otherwise shows the
            // unblurred source) without moving the fade or sampling origin; the box and its mask keep their size.
            val bleed = 2
            val child = measurable.measure(constraints.copy(
                minWidth = constraints.minWidth + 2 * bleed, maxWidth = constraints.maxWidth + 2 * bleed,
                minHeight = constraints.minHeight + bleed, maxHeight = constraints.maxHeight + bleed))
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(-bleed, -bleed) }
        }, factory = { MiGlassView(it, rounded = false) },
            update = { view ->
                view.report = report
                view.configure(radius, true, if (visible) 1f else 0f)
            }, onRelease = { it.release() })
    }
}
