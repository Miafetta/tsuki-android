package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.ReminderLaunch
import cafe.miafetta.tsuki.core.reminder.ReminderNotice
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.providers.SklandRole

// What a reminder notification shows and where it goes, kept free of Android types so that JVM tests cover it;
// ReminderNotifier posts it.
internal object ReminderNotifications {
    // One channel per game, created only for the games Tsuki supports. Users keep their sound and vibration choices
    // under the channel ID, so an ID never changes once released.
    fun channelId(game: SklandGame) = "reminder.${game.providerId}"

    // One notification per rule (a role and a metric): a new notice of the rule replaces the old one, and notices of
    // other metrics or roles stay. Role keys never contain "#".
    fun tag(subject: ReminderSubject, metricId: String) = "${ReminderLaunch.roleKey(subject)}#$metricId"

    // "{role}（{game}）". Arknights labels keep the legacy "<name> · <server> · <uid>" form, of which only the name is
    // shown; Tsuki copy never shows "·". Newer games store the nickname as the label.
    fun title(role: SklandRole): String {
        val name = when (role.game) {
            SklandGame.ARKNIGHTS -> role.label.substringBefore(" · ").ifBlank { role.uid }
            SklandGame.ENDFIELD -> role.label
        }
        return "$name（${role.game.displayName}）"
    }

    fun post(notice: ReminderNotice, role: SklandRole) =
        ReminderPost(notice.subject, role.game, channelId(role.game), tag(notice.subject, notice.metricId), title(role), notice.body)
}

// One notification to post: its role (where a tap leads), game and channel, tag, title and text.
internal data class ReminderPost(val subject: ReminderSubject, val game: SklandGame, val channelId: String, val tag: String,
    val title: String, val body: String)
