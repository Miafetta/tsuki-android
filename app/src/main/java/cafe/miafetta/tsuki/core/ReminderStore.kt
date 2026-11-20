package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord
import java.io.File
import org.json.JSONException
import org.json.JSONObject

// Reminder rules, their trigger records and the roles' check times in one file (ReminderJson). It lives in the
// :widgetProvider process under noBackupFilesDir: it holds account and role IDs, though no credentials. A missing file
// means the master switch is off and there are no rules; nothing is written until something changes.
//
// A file that cannot be read as a whole fails every operation with a STORAGE failure and is left as it is; only clear()
// replaces it. A write that would make the file unreadable (more rules, roles or bytes than reading accepts) is refused
// with an IllegalArgumentException instead.
internal class ReminderStore(private val file: File) {
    @Synchronized fun read(): ReminderData {
        if (!file.exists()) return ReminderData()
        if (file.length() > MAX_BYTES) throw unreadable()
        val text = file.readText(Charsets.UTF_8)
        return try {
            ReminderJson.read(JSONObject(text))
        } catch (_: JSONException) {
            throw unreadable()
        } catch (_: IllegalArgumentException) {
            throw unreadable()
        }
    }

    // Saves [rule] in place of the rule of the same role and metric. A disabled rule keeps the empty record, so that
    // enabling it again starts over; otherwise the old record stays, also when the mode or threshold changed: the
    // evaluator starts such a record over itself (TriggerRecord.keptFor).
    @Synchronized fun save(rule: ReminderRule): ReminderData = update { data ->
        val old = data.entry(rule.subject, rule.metricId)
        val entry = ReminderEntry(rule, if (rule.enabled) old?.record ?: TriggerRecord() else TriggerRecord())
        data.copy(entries = if (old == null) data.entries + entry else data.entries.map { if (it == old) entry else it })
    }

    @Synchronized fun delete(subject: ReminderSubject, metricId: String): ReminderData = update { data ->
        data.copy(entries = data.entries.filterNot { it.rule.subject == subject && it.rule.metricId == metricId })
    }

    // Turning the master switch on from off starts every rule over, so that nothing that happened while reminders
    // were off is reported late; switching it on again while on changes nothing.
    @Synchronized fun setEnabled(enabled: Boolean): ReminderData = update { data ->
        if (enabled && !data.enabled) data.copy(enabled = true, entries = data.entries.map { it.copy(record = TriggerRecord()) })
        else data.copy(enabled = enabled)
    }

    // Keeps the records the evaluator returned, keyed by the entries they were judged from. An entry that changed in
    // the meantime (the rule was edited or removed, or the master switch reset its record) keeps its current record:
    // a judgement of the old state must not overwrite the new one. Returns how many records were written.
    @Synchronized fun writeRecords(judged: Map<ReminderEntry, TriggerRecord>): Int {
        var written = 0
        update { data ->
            data.copy(entries = data.entries.map { entry ->
                judged[entry]?.let { record ->
                    written++
                    entry.copy(record = record)
                } ?: entry
            })
        }
        return written
    }

    @Synchronized fun checks(subject: ReminderSubject): ReminderCheckTimes = read().checks[subject] ?: ReminderCheckTimes()

    @Synchronized fun writeChecks(subject: ReminderSubject, times: ReminderCheckTimes): ReminderData = update { data ->
        data.copy(checks = if (times == ReminderCheckTimes()) data.checks - subject else data.checks + (subject to times))
    }

    // Removes the rules and check times of every role of [accountId]; returns the roles removed.
    @Synchronized fun deleteAccount(accountId: String): Set<ReminderSubject> = removeWhere { it.accountId == accountId }

    // Removes the rules and check times of the roles of [accountId] that are not in [existing]; returns the roles removed.
    @Synchronized fun retainRoles(accountId: String, existing: Set<ReminderSubject>): Set<ReminderSubject> =
        removeWhere { it.accountId == accountId && it !in existing }

    // Removes every rule and check time and keeps the master switch. A file that cannot be read is replaced, with the
    // switch off: clearing is what the user asked for, and nothing in it can be kept.
    @Synchronized fun clear() {
        val data = try {
            read()
        } catch (_: DataSourceFailure) {
            null
        }
        if (data == null) write(ReminderData())
        else if (data.subjects.isNotEmpty()) write(ReminderData(data.enabled))
    }

    private fun removeWhere(predicate: (ReminderSubject) -> Boolean): Set<ReminderSubject> {
        val data = read()
        val removed = data.subjects.filter(predicate).toSet()
        if (removed.isNotEmpty()) {
            write(data.copy(entries = data.entries.filterNot { it.rule.subject in removed }, checks = data.checks - removed))
        }
        return removed
    }

    private inline fun update(transform: (ReminderData) -> ReminderData): ReminderData {
        val current = read()
        val next = transform(current)
        if (next != current) write(next)
        return next
    }

    private fun write(data: ReminderData) {
        require(data.entries.size <= MAX_RULES && data.subjects.size <= MAX_SUBJECTS) { "Reminder limit reached" }
        val bytes = ReminderJson.write(data).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Reminder limit reached" }
        writeAtomic(file, bytes)
    }

    private fun unreadable() = DataSourceFailure(RefreshFailure.STORAGE)

    companion object {
        const val MAX_BYTES = 128 * 1024
        // Far above what anyone sets up (a role offers at most seven reminder metrics); they bound what a damaged
        // file can make the reader do.
        const val MAX_RULES = 500
        const val MAX_SUBJECTS = 500
    }
}
