package cafe.miafetta.tsuki.ui.state

import android.app.NotificationManager
import cafe.miafetta.tsuki.providers.SklandGame

// What the system lets reminders show: whether the app may post notifications at all, and each game channel's
// importance (null while the channel has not been created yet).
internal data class ReminderSystemStatus(val notificationsAllowed: Boolean = true,
    val channelImportance: Map<SklandGame, Int?> = emptyMap())

// Something in the system settings that keeps reminders from showing; each one is a row that opens its settings page.
internal sealed interface ReminderProblem {
    val title: String

    data object NotificationsOff : ReminderProblem {
        override val title = "通知权限未开启"
    }

    data class ChannelOff(val game: SklandGame) : ReminderProblem {
        override val title get() = "${game.displayName}的通知已关闭"
    }

    companion object {
        // Nothing is reported while reminders are off. A channel that does not exist yet is not off: the widget process
        // creates it before its first notice (widget/ReminderNotifier.kt).
        fun of(enabled: Boolean, status: ReminderSystemStatus): List<ReminderProblem> {
            if (!enabled) return emptyList()
            val channels = SklandGame.entries.filter { status.channelImportance[it] == NotificationManager.IMPORTANCE_NONE }
                .map(::ChannelOff)
            return (if (status.notificationsAllowed) emptyList() else listOf(NotificationsOff)) + channels
        }
    }
}
