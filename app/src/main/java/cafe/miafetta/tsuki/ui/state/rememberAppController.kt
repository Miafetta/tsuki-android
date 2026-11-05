package cafe.miafetta.tsuki.ui.state

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import top.yukonga.miuix.kmp.nav.core.*

@Composable
internal fun rememberAppController(activity: Activity, initialPage: Int, resumeTick: Int, configuringId: Int?,
    overviewWidgetId: Int?, onConfigured: () -> Unit): AppController {
    val state = remember(activity) {
        ViewModelProvider(activity as ComponentActivity, viewModelFactory {
            initializer { AppState(activity.applicationContext) }
        })[AppState::class.java]
    }
    val prefs = remember { activity.getSharedPreferences(UiPreferences.FILE, 0) }
    val nav = rememberNavController(AppRoute.Root)
    val draft = rememberAccountDraft()
    val session = rememberSaveable(saver = UiSession.Saver) {
        UiSession(initialPage.coerceIn(AppPage.OVERVIEW, AppPage.ME), prefs.getString(UiPreferences.ROLE, "") ?: "",
            configuringId?.let { ConfigSheet.Edit(it) } ?: ConfigSheet.Closed())
    }
    val controller = remember(state, nav, draft, session) { AppController(state, prefs, nav, draft, session, activity) }
    ControllerNavigationEffects(controller, activity, configuringId, onConfigured)
    ControllerDataEffects(controller, activity, resumeTick, overviewWidgetId)
    return controller
}
