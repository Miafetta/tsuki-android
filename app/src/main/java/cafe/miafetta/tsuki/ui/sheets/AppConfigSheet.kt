package cafe.miafetta.tsuki.ui.sheets

import cafe.miafetta.tsuki.StorageContract as Storage

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.runtime.*
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.ui.previews
import cafe.miafetta.tsuki.ui.state.AppController
import cafe.miafetta.tsuki.ui.state.AppPage
import cafe.miafetta.tsuki.ui.state.ConfigSheet
import cafe.miafetta.tsuki.widget.WidgetRuntime
import kotlinx.coroutines.launch

@Composable
internal fun AppConfigSheet(controller: AppController, activity: Activity, configuringId: Int?,
    active: Boolean, onConfigurationClosed: () -> Unit) = with(controller) {
    if (state.loaded) {
        val sheet = configSheet.content
        val widgetId = (sheet as? ConfigSheet.Edit)?.widgetId
        val current = state.widgets.firstOrNull { it.id == widgetId }
        val choices = if (sheet?.diagnostic == true && BuildConfig.DEBUG) state.choices else state.roles
        ConfigEditor(activity, choices, current, state.busy, state.error,
            onDismiss = {
                if (!state.busy) {
                    configSheet = configSheet.close()
                    state.error = ""
                }
            },
            onAccounts = {
                if (configuringId == null) {
                    configSheet = configSheet.close()
                    page = AppPage.ME
                    addAccount()
                } else activity.startActivity(Intent(activity, SklandAccountActivity::class.java).putExtra("addAccount", true))
            }, onSave = { role, chosenTheme, failure ->
                val extras = role.extras().apply {
                    putString(Storage.PROVIDER_ID, role.provider)
                    putString(Storage.FEATURE_ID, WidgetRuntime.FEATURE)
                    putString(Storage.THEME, chosenTheme)
                    putBoolean(Storage.SIMULATE_FAILURE, failure)
                }
                if (sheet is ConfigSheet.New) {
                    configSheet = configSheet.close()
                    pin(extras)
                } else {
                    extras.putInt(Storage.WIDGET_ID, widgetId ?: return@ConfigEditor)
                    scope.launch { state.action(Storage.CONFIGURE, extras, inlineError = true) }
                }
            }, show = configSheet is ConfigSheet.Content && active,
            previews = previews, previewSnapshot = state::previewSnapshot,
            onDismissFinished = {
                if (configuringId != null && configSheet is ConfigSheet.Closed) onConfigurationClosed()
            }, showTools = sheet?.diagnostic == true,
            widgetId = widgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID, initialTheme = sheet?.theme ?: "LIGHT",
            overviewPreview = selected?.key?.let { it to homeSnapshot })
    }
}
