package cafe.miafetta.tsuki

import android.content.Intent

internal fun isWidgetOverviewIntent(intent: Intent): Boolean = widgetOverviewId(intent) != null

// Widget instance whose body tap opened the overview, or null for any other launch.
internal fun widgetOverviewId(intent: Intent): Int? {
    if (intent.getBooleanExtra("returnWidgetResult", false)) return null
    val uri = intent.data ?: return null
    // Older launcher RemoteViews still carry a direct configure URI to MainActivity.
    if (uri.scheme != "tsuki" || uri.host !in listOf("overview", "configure") || uri.pathSegments.size != 1) return null
    return uri.pathSegments.single().toIntOrNull()?.takeIf { it > 0 }
}
