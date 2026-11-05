package cafe.miafetta.tsuki.widget

import androidx.core.net.toUri
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import cafe.miafetta.tsuki.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class ProbeWidgetProvider : AppWidgetProvider() {
    protected abstract val label: Int
    protected open val xiaomi: Boolean = false

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MANUAL_REFRESH -> render(context, WidgetTargets.resolve(owned(context), intent), R.string.event_manual)
            WidgetTargets.XIAOMI_UPDATE -> if (xiaomi) {
                Log.i(TAG, "${javaClass.simpleName} received ${WidgetTargets.XIAOMI_UPDATE}")
                render(context, WidgetTargets.resolve(owned(context), intent), R.string.event_exposure)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, WidgetTargets.resolve(owned(context), ids), R.string.event_update)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager,
        id: Int, newOptions: Bundle) {
        render(context, WidgetTargets.resolve(owned(context), intArrayOf(id)), R.string.event_options)
    }

    private fun owned(context: Context) = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, javaClass))

    private fun render(context: Context, ids: IntArray, event: Int) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val manager = AppWidgetManager.getInstance(context)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.probe_widget)
            views.setTextViewText(R.id.probe_title, context.getString(R.string.probe_title,
                context.getString(label), id))
            views.setTextViewText(R.id.probe_status,
                context.getString(R.string.probe_status, time, context.getString(event)))
            val intent = Intent(context, javaClass).setAction(MANUAL_REFRESH)
                .setData("tsuki://probe/${javaClass.simpleName}/$id".toUri())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            views.setOnClickPendingIntent(R.id.probe_refresh, PendingIntent.getBroadcast(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
            Log.i(TAG, "${javaClass.simpleName} id=$id event=${context.getString(event)} time=$time")
        }
    }

    companion object {
        const val TAG = "TsukiProbe"
        private const val MANUAL_REFRESH = "cafe.miafetta.tsuki.action.PROBE_REFRESH"
    }
}

class XiaomiProbeProvider : ProbeWidgetProvider() {
    override val label = R.string.probe_xiaomi
    override val xiaomi = true
}
