package cafe.miafetta.tsuki.widget

import android.appwidget.AppWidgetManager
import android.content.Intent

// Shared by the status widget and the diagnostic probe: never touch instances owned by another provider.
internal object WidgetTargets {
    const val XIAOMI_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"

    fun resolve(owned: IntArray, requested: IntArray?, single: Int? = null): IntArray {
        val candidates = requested ?: single?.let { intArrayOf(it) } ?: owned
        return candidates.filter { it in owned }.distinct().toIntArray()
    }

    fun resolve(owned: IntArray, intent: Intent) = resolve(
        owned, intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
        if (intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        else null,
    )
}
