package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage

import android.os.Bundle
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import org.json.JSONException

// The four reminder operations of the widget process. Each returns the whole reminder state after the call, or null
// when the call failed or its answer could not be read. Blocking: callers run them on the IO dispatcher.
internal class ReminderCalls(private val call: (String, Bundle) -> Bundle) {
    fun read() = run(Storage.REMINDERS, Bundle())

    fun save(rule: ReminderRule) = run(Storage.SAVE_REMINDER, subject(rule.subject).apply {
        putString(Storage.METRIC_ID, rule.metricId)
        putString(Storage.MODE, rule.mode.name)
        // The widget process takes a threshold only for the modes that have one.
        rule.threshold?.let { putInt(Storage.THRESHOLD, it) }
        putBoolean(Storage.ENABLED, rule.enabled)
    })

    fun delete(subject: ReminderSubject, metricId: String) =
        run(Storage.DELETE_REMINDER, subject(subject).apply { putString(Storage.METRIC_ID, metricId) })

    fun setEnabled(enabled: Boolean) = run(Storage.SET_REMINDERS_ENABLED, Bundle().apply { putBoolean(Storage.ENABLED, enabled) })

    private fun subject(subject: ReminderSubject) = Bundle().apply {
        putString(Storage.PROVIDER_ID, subject.providerId)
        putString(Storage.ACCOUNT_ID, subject.accountId)
        putString(Storage.CHARACTER_ID, subject.characterId)
    }

    private fun run(method: String, extras: Bundle): UiReminders? {
        val result = call(method, extras)
        if (result.getBoolean(Storage.ERROR)) return null
        val json = result.getString(Storage.JSON) ?: return null
        return try {
            UiReminders.parse(json)
        } catch (_: JSONException) {
            null
        }
    }
}
