package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.StorageContract as Storage

import cafe.miafetta.tsuki.core.ReminderData
import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.RuleCheck
import cafe.miafetta.tsuki.providers.SklandAccount
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.providers.SklandRole
import org.json.JSONArray
import org.json.JSONObject

// Which roles may hold reminders, and what the app process may read of them. Kept free of Android types so that JVM
// tests cover the checks behind WidgetRuntimeReminders.kt and WidgetStorageProvider.
internal object ReminderAccess {
    // The roles of a stored Skland account, as reminder subjects. The debug fixture is no Skland game and its roles are
    // on no account, so they never hold reminders.
    fun subjects(accountId: String, roles: List<SklandRole>): Set<ReminderSubject> = roles.map { subject(accountId, it) }.toSet()

    fun subject(accountId: String, role: SklandRole) = ReminderSubject(role.game.providerId, accountId, role.uid)

    fun subjects(accounts: List<SklandAccount>): Set<ReminderSubject> = accounts.flatMap { subjects(it.id, it.roles) }.toSet()

    // A rule may be saved when its metric offers its mode, the metric belongs to the role's game (metric IDs start with
    // the game's app code, "arknights." or "endfield."), and its role is a role of a stored account now, the same test
    // as WidgetRuntime.dashboard. Whether a value threshold fits the current limit is for the settings page, which has
    // the latest data; the evaluator holds back a rule that no longer fits.
    fun accepts(rule: ReminderRule, accounts: List<SklandAccount>): Boolean {
        val game = SklandGame.fromProvider(rule.subject.providerId) ?: return false
        return ReminderCapabilities.check(rule) == RuleCheck.VALID && rule.metricId.substringBefore('.') == game.appCode &&
            rule.subject in subjects(accounts)
    }

    // The master switch and every rule, for the settings page. Trigger records and check times stay in this process.
    fun overview(data: ReminderData): JSONObject = JSONObject().put(Storage.ENABLED, data.enabled)
        .put(Storage.RULES, JSONArray(data.entries.map { (rule, _) ->
            JSONObject().put(Storage.PROVIDER_ID, rule.subject.providerId).put(Storage.ACCOUNT_ID, rule.subject.accountId)
                .put(Storage.CHARACTER_ID, rule.subject.characterId).put(Storage.METRIC_ID, rule.metricId)
                .put(Storage.MODE, rule.mode.name).put(Storage.THRESHOLD, rule.threshold).put(Storage.ENABLED, rule.enabled)
        }))
}
