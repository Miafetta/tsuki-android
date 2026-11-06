package cafe.miafetta.tsuki.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Log
import cafe.miafetta.tsuki.core.WidgetTheme

open class StatusWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MANUAL_REFRESH || intent.action == WidgetTargets.XIAOMI_UPDATE) {
            WidgetTargets.resolve(owned(context), intent).forEach { WidgetRuntime.get(context).update(it) }
        } else super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetTargets.resolve(owned(context), ids).forEach { WidgetRuntime.get(context).update(it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.filter { it > 0 && it !in owned(context) }.forEach { id ->
            try { WidgetRuntime.get(context).delete(id) }
            catch (_: java.io.IOException) { Log.w("TsukiWidget", "Unable to delete widget data id=$id") }
        }
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        if (restore(context, oldIds, newIds)) {
            val manager = AppWidgetManager.getInstance(context)
            newIds.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                options.putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                manager.updateAppWidgetOptions(id, options)
                WidgetRuntime.get(context).update(id)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        if (id !in owned(context)) return
        if (options.getBoolean("miuiIdChanged") && !options.getBoolean("miuiIdChangedComplete")) {
            val oldIds = options.getIntArray("miuiOldIds")
            val newIds = options.getIntArray("miuiNewIds")
            if (oldIds != null && newIds != null && restore(context, oldIds, newIds)) {
                options.putBoolean("miuiIdChangedComplete", true)
                newIds.forEach { newId ->
                    manager.updateAppWidgetOptions(newId, options)
                    WidgetRuntime.get(context).update(newId)
                }
                return
            }
        }
        WidgetRuntime.get(context).redraw(id)
    }

    private fun restore(context: Context, oldIds: IntArray, newIds: IntArray): Boolean {
        val owned = owned(context)
        if (newIds.any { it !in owned } || oldIds.any { it in owned && it !in newIds }) return false
        return try {
            WidgetRuntime.get(context).remap(oldIds, newIds)
            true
        } catch (_: java.io.IOException) {
            Log.w("TsukiWidget", "Unable to restore widget data")
            false
        } catch (_: IllegalArgumentException) {
            Log.w("TsukiWidget", "Invalid widget restore mapping")
            false
        }
    }

    companion object {
        const val MANUAL_REFRESH = "cafe.miafetta.tsuki.action.STATUS_REFRESH"
        // Refresh, configuration and pin callbacks ignore instances of any provider missing from this list.
        private val providers = listOf(StatusWidgetProvider::class.java, OpaqueWidgetProvider::class.java,
            EndfieldWidgetProvider::class.java)

        fun owned(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return providers.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).asList() }.toIntArray()
        }
        // Arknights has a light and a dark card; the Endfield card is always dark, like the official widget.
        internal fun providerClass(feature: String, theme: WidgetTheme): Class<out StatusWidgetProvider> = when {
            feature == WidgetRuntime.ENDFIELD_FEATURE -> EndfieldWidgetProvider::class.java
            theme == WidgetTheme.DARK -> OpaqueWidgetProvider::class.java
            else -> StatusWidgetProvider::class.java
        }
        // The palettes a feature offers; only Arknights lets the user choose.
        internal fun themes(feature: String) =
            if (feature == WidgetRuntime.ENDFIELD_FEATURE) listOf(WidgetTheme.DARK) else listOf(WidgetTheme.LIGHT, WidgetTheme.DARK)
        private fun className(context: Context, id: Int) =
            AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className
        // Only the light Arknights provider keeps the host's blur; the others draw their own dark surface.
        internal fun theme(context: Context, id: Int): WidgetTheme =
            if (className(context, id) == StatusWidgetProvider::class.java.name) WidgetTheme.LIGHT else WidgetTheme.DARK
        // The widget feature an instance shows, from its provider; Arknights for an unknown instance.
        internal fun feature(context: Context, id: Int) =
            if (className(context, id) == EndfieldWidgetProvider::class.java.name) WidgetRuntime.ENDFIELD_FEATURE else WidgetRuntime.FEATURE
    }
}

// shortcut: existing IDs cannot change provider; re-add cards to change the launcher material.
class OpaqueWidgetProvider : StatusWidgetProvider()

// The Endfield card, always dark; refresh, pin and configuration share StatusWidgetProvider's handling.
class EndfieldWidgetProvider : StatusWidgetProvider()
