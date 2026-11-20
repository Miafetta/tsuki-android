package cafe.miafetta.tsuki.ui.state

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.net.toUri
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.widget.ReminderNotifications

// The system side of reminders as the settings page reads it, and the system settings pages it opens. Tsuki only
// reads these settings and leads to them; it never changes them (docs/DEVELOPMENT_CONSTRAINTS.md §2).
internal object ReminderSystem {
    fun permissionGranted(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun status(context: Context): ReminderSystemStatus {
        val manager = context.getSystemService(NotificationManager::class.java)
        return ReminderSystemStatus(manager.areNotificationsEnabled() && permissionGranted(context),
            SklandGame.entries.associateWith { manager.getNotificationChannel(ReminderNotifications.channelId(it))?.importance })
    }

    fun settings(context: Context, problem: ReminderProblem): Intent = when (problem) {
        ReminderProblem.NotificationsOff -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        is ReminderProblem.ChannelOff -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifications.channelId(problem.game))
    }

    // Autostart and battery settings differ between ROMs; the app details page leads to both on HyperOS. There the
    // standard request reaches the security center's page only through a settings trampoline, which starts it as an
    // interrupting activity and drops the transition, so the security center's own action is tried first; other ROMs,
    // or a HyperOS without that page, take the standard request. Tried in order by [open] rather than resolved first:
    // package visibility would hide the security center from a lookup without a manifest query.
    fun appDetails(context: Context): List<Intent> = listOf(
        Intent(HYPEROS_APP_DETAILS).setPackage(HYPEROS_SECURITY_CENTER).putExtra(HYPEROS_PACKAGE_EXTRA, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))

    // Starts the first of [intents] this system has; false when it has none of them.
    fun open(context: Context, intents: List<Intent>): Boolean = intents.any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private const val HYPEROS_APP_DETAILS = "miui.intent.action.APP_MANAGER_APPLICATION_DETAIL"
    private const val HYPEROS_SECURITY_CENTER = "com.miui.securitycenter"
    private const val HYPEROS_PACKAGE_EXTRA = "package_name"
}
