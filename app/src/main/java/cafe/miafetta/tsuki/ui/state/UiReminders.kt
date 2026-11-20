package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage

import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.RuleCheck
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// The reminder settings as the settings page shows them: the master switch and every stored rule. Each reminder
// operation returns this whole state (StorageContract.REMINDERS), so the page never merges partial answers.
internal data class UiReminders(val enabled: Boolean = false, val rules: List<ReminderRule> = emptyList()) {
    fun rule(subject: ReminderSubject, metricId: String) =
        rules.firstOrNull { it.subject == subject && it.metricId == metricId }

    fun with(rule: ReminderRule) = copy(rules = rules.filterNot { it.subject == rule.subject && it.metricId == rule.metricId } + rule)

    fun without(subject: ReminderSubject, metricId: String) =
        copy(rules = rules.filterNot { it.subject == subject && it.metricId == metricId })

    companion object {
        // A rule this version cannot show (an unknown metric or mode, or a mode its metric does not offer) is skipped
        // rather than failing the page; the stored file keeps it. A malformed document throws JSONException.
        fun parse(text: String): UiReminders {
            val json = JSONObject(text)
            val rules = json.optJSONArray(Storage.RULES) ?: JSONArray()
            return UiReminders(json.optBoolean(Storage.ENABLED, false),
                (0 until rules.length()).mapNotNull { index -> rules.optJSONObject(index)?.let(::rule) })
        }

        private fun rule(json: JSONObject): ReminderRule? = try {
            val mode = ReminderMode.entries.firstOrNull { it.name == json.optString(Storage.MODE) }
            val threshold = if (json.isNull(Storage.THRESHOLD)) null else json.getInt(Storage.THRESHOLD)
            val subject = ReminderSubject(json.getString(Storage.PROVIDER_ID), json.getString(Storage.ACCOUNT_ID),
                json.getString(Storage.CHARACTER_ID))
            mode?.let { ReminderRule(subject, json.getString(Storage.METRIC_ID), it, threshold, json.optBoolean(Storage.ENABLED, true)) }
                ?.takeIf { ReminderCapabilities.check(it) == RuleCheck.VALID }
        } catch (_: JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
