package cafe.miafetta.tsuki.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.ReminderLaunch
import cafe.miafetta.tsuki.core.ReminderDelivery
import cafe.miafetta.tsuki.providers.SklandGame

// Reminder notifications of the :widgetProvider process. A notice the system would not show (notifications off, or its
// game's channel off) is not posted; its rule was still judged and its record kept, so nothing is sent late once
// notifications are back.
internal class ReminderNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun delivery() = ReminderDelivery(manager.areNotificationsEnabled(),
        SklandGame.entries.filter(::blocked).map { it.providerId }.toSet())

    // A channel per supported game, named after the game; sound and vibration are the system's defaults until the user
    // changes them. Creating an existing channel again only updates its name.
    fun createChannels() {
        manager.createNotificationChannels(SklandGame.entries.map { game ->
            NotificationChannel(ReminderNotifications.channelId(game), game.displayName, NotificationManager.IMPORTANCE_DEFAULT)
        })
    }

    fun post(posts: List<ReminderPost>) {
        if (posts.isEmpty() || !manager.areNotificationsEnabled()) return
        createChannels()
        posts.filterNot { blocked(it.game) }.forEach { manager.notify(it.tag, NOTIFICATION_ID, notification(it)) }
    }

    // A channel not created yet is not off: it is created before the first notice.
    private fun blocked(game: SklandGame) =
        manager.getNotificationChannel(ReminderNotifications.channelId(game))?.importance == NotificationManager.IMPORTANCE_NONE

    // A tap opens the overview of the notice's role and removes the notification.
    private fun notification(post: ReminderPost): Notification {
        val open = PendingIntent.getActivity(context, 0, ReminderLaunch.intent(context, post.subject),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, post.channelId)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(post.title)
            .setContentText(post.body)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private companion object {
        // Tags tell the notifications apart (ReminderNotifications.tag); the ID is the same for all of them.
        const val NOTIFICATION_ID = 1
    }
}
