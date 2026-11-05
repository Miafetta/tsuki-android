package cafe.miafetta.tsuki

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import cafe.miafetta.tsuki.ui.components.setTsukiContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import cafe.miafetta.tsuki.ui.TsukiApp
import cafe.miafetta.tsuki.ui.state.AppPage
import cafe.miafetta.tsuki.widget.StatusWidgetProvider

class WidgetConfigActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = widgetConfigId(intent)
        if (id !in StatusWidgetProvider.owned(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setTsukiContent {
            TsukiApp(this, initialPage = AppPage.WIDGETS, resumeTick = resumeTick.intValue, configuringId = id,
                onConfigured = { setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)) },
                onConfigurationClosed = { finish() })
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        // The sheet already supplies the exit animation; do not slide the whole page out a second time.
        if (android.os.Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        super.finish()
        if (android.os.Build.VERSION.SDK_INT < 34) overridePendingTransition(0, 0)
    }
}

internal fun widgetConfigId(intent: Intent): Int {
    if (intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
        return intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    val uri = intent.data ?: return -1
    if (uri.scheme != "tsuki" || uri.host != "configure" || uri.pathSegments.size != 1) return -1
    val id = uri.pathSegments.single().toIntOrNull()?.takeIf { it > 0 } ?: return -1
    val query = uri.getQueryParameter("miuiWidgetId")
    return if (query == null || query.toIntOrNull() == id) id else -1
}
