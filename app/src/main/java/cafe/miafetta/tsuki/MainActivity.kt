package cafe.miafetta.tsuki

import android.os.Bundle
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import cafe.miafetta.tsuki.ui.TsukiApp
import cafe.miafetta.tsuki.widget.StatusWidgetProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    private val editingWidget = mutableStateOf<Int?>(null)
    private var lastStoppedAt = android.os.SystemClock.elapsedRealtime()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val overviewId = widgetOverviewId(intent)
        val fromWidget = overviewId != null
        // Recents relaunches with the widget intent; only a real tap may replace the role the user chose since.
        val replayed = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val id = if (fromWidget) -1 else widgetConfigId(intent)
        if (!fromWidget && intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) && id !in StatusWidgetProvider.owned(this)) {
            finish()
            return
        }
        editingWidget.value = if (fromWidget) null else (savedInstanceState?.getInt("editingWidget", -1) ?: id)
            .takeIf { it in StatusWidgetProvider.owned(this) }
        setResult(RESULT_CANCELED)
        enableEdgeToEdge()
        setContent { TsukiApp(this, initialPage = if (editingWidget.value == null) 0 else 1,
            resumeTick = resumeTick.intValue, configuringId = editingWidget.value,
            overviewWidgetId = overviewId.takeUnless { replayed },
            onConfigured = { setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, editingWidget.value ?: -1)) },
            onConfigurationClosed = {
                editingWidget.value = null
                if (intent.getBooleanExtra("returnWidgetResult", false)) finish()
            }) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("editingWidget", editingWidget.value ?: -1)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isWidgetOverviewIntent(intent)) {
            // The replacement instance starts without saved state, so the tapped widget's role is applied again.
            startActivity(Intent(this, MainActivity::class.java).setData(intent.data).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            return
        }
        val id = widgetConfigId(intent)
        if (id !in StatusWidgetProvider.owned(this)) return
        setIntent(intent)
        editingWidget.value = id
    }
    override fun onStop() {
        lastStoppedAt = android.os.SystemClock.elapsedRealtime()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        // HyperOS briefly pauses/resumes us before showing pin confirmation; only a real stop cancels a placement.
        val stoppedAt = lastStoppedAt
        lifecycleScope.launch(Dispatchers.IO) { storageCall(StorageContract.CLEAR_PIN, Bundle().apply {
            putLong(StorageContract.PIN_CANCEL_BEFORE, stoppedAt)
        }) }
        resumeTick.intValue++
    }
}
