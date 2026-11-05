package cafe.miafetta.tsuki.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.WidgetConfigActivity
import cafe.miafetta.tsuki.core.WidgetRecord

// Where a tap on the card body leads.
internal enum class WidgetTapTarget { OVERVIEW, CONFIGURE }

// An unconfigured card asks to be tapped for configuration, so that tap must open it. A storage error keeps Overview:
// the card cannot tell whether the instance is configured, and Overview offers the retry.
internal fun widgetTapTarget(record: WidgetRecord?, storageError: Boolean) =
    if (record == null && !storageError) WidgetTapTarget.CONFIGURE else WidgetTapTarget.OVERVIEW

// The launcher's edit entry (miuiEditUri) and the unconfigured tap share this URI; WidgetConfigActivity parses it.
internal fun widgetConfigureUri(id: Int) = "tsuki://configure/$id?miuiWidgetId=$id"

internal fun widgetOverviewIntent(context: Context, id: Int): Intent =
    Intent(context, MainActivity::class.java).setData("tsuki://overview/$id".toUri())
        // Standard launch mode replaces a warm activity so its saved page/sheet cannot override Overview.
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

// Explicit component and its own data: the PendingIntent shares the Overview request code but must not replace it.
internal fun widgetConfigureIntent(context: Context, id: Int): Intent =
    Intent(context, WidgetConfigActivity::class.java).setData(widgetConfigureUri(id).toUri())
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

internal fun bindWidgetActions(context: Context, id: Int, views: RemoteViews, target: WidgetTapTarget) {
    if (id <= 0) return
    val open = if (target == WidgetTapTarget.CONFIGURE) widgetConfigureIntent(context, id) else widgetOverviewIntent(context, id)
    views.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(context, id, open,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    val refresh = Intent(context, StatusWidgetProvider::class.java).setAction(StatusWidgetProvider.MANUAL_REFRESH)
        .setData("tsuki://refresh/$id".toUri()).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    views.setOnClickPendingIntent(R.id.status_refresh_hit, PendingIntent.getBroadcast(context, id, refresh,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
}
