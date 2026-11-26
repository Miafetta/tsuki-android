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
    overviewWidgetId: Int?, overviewRoleKey: String?, onConfigured: () -> Unit): AppController {
    // The first overview read starts here, before the rest of the screen composes; it is idempotent per ViewModel and
    // the first reload joins it (AppState.load).
    val host = activity as ComponentActivity
    val state = remember(host) {
        ViewModelProvider(host, viewModelFactory {
            initializer { AppState(activity.applicationContext) }
        })[AppState::class.java].also { it.startInitialLoad() }
    }
    val prefs = remember { activity.getSharedPreferences(UiPreferences.FILE, 0) }
    // The type argument picks the saved-state serializer: inferred as AppRoute.Root, every saved entry would come back as
    // Root and a recreation on a secondary page would crash on the duplicate key.
    val nav = rememberNavController<AppRoute>(AppRoute.Root)
    val draft = rememberAccountDraft()
    val session = rememberSaveable(saver = UiSession.Saver) {
        UiSession(initialPage.coerceIn(AppPage.OVERVIEW, AppPage.ME), prefs.getString(UiPreferences.ROLE, "") ?: "",
            configuringId?.let { ConfigSheet.Edit(it) } ?: ConfigSheet.Closed())
    }
    val controller = remember(state, nav, draft, session) { AppController(state, prefs, nav, draft, session, host) }
    ControllerNavigationEffects(controller, activity, configuringId, onConfigured)
    ControllerDataEffects(controller, activity, resumeTick, overviewWidgetId, overviewRoleKey)
    return controller
}
