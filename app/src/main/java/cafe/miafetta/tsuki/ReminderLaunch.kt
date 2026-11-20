package cafe.miafetta.tsuki

import android.content.Context
import android.content.Intent
import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandGame

// A tap on a reminder notification opens the overview of the notice's role, whether or not a widget shows it. The role
// travels as extras of an explicit Intent to MainActivity, never as a tsuki:// URI, so the widget deep links stay as
// they are (WidgetLaunch.kt). The system compares PendingIntents without their extras, so each role's Intent carries
// the role key as its identifier: otherwise a later notification could change where an earlier one leads.
internal object ReminderLaunch {
    // The keys of the role bundles of StorageContract. Notifications already posted keep them across updates.
    const val PROVIDER_ID = Storage.PROVIDER_ID
    const val ACCOUNT_ID = Storage.ACCOUNT_ID
    const val CHARACTER_ID = Storage.CHARACTER_ID

    // A role's identity in one string, for Intent identifiers and notification tags. No part contains "/": account IDs
    // are UUIDs, role IDs are digits or Endfield's "<server>~<role>", and data source IDs are dotted words.
    fun roleKey(subject: ReminderSubject) = "${subject.providerId}/${subject.accountId}/${subject.characterId}"

    fun extras(subject: ReminderSubject) =
        mapOf(PROVIDER_ID to subject.providerId, ACCOUNT_ID to subject.accountId, CHARACTER_ID to subject.characterId)

    // Like the widget's overview Intent (widgetOverviewIntent), a tap replaces a warm activity, so that its saved page
    // cannot override the overview; a notification starts it outside any task.
    fun intent(context: Context, subject: ReminderSubject): Intent = Intent(context, MainActivity::class.java)
        .setIdentifier("reminder/${roleKey(subject)}")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .apply { extras(subject).forEach { (key, value) -> putExtra(key, value) } }

    // The role a reminder notification opened, or null for any other launch.
    fun role(intent: Intent): ReminderSubject? = role { intent.getStringExtra(it) }

    // The role in [extra] (by key), or null when a key is missing or blank, or the data source is no Skland game.
    fun role(extra: (String) -> String?): ReminderSubject? {
        val providerId = extra(PROVIDER_ID) ?: return null
        val accountId = extra(ACCOUNT_ID) ?: return null
        val characterId = extra(CHARACTER_ID) ?: return null
        if (SklandGame.fromProvider(providerId) == null) return null
        return try {
            ReminderSubject(providerId, accountId, characterId)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
