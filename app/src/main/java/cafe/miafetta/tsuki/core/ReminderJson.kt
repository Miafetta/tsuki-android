package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// The reminder file of ReminderStore: the master switch, then one object per role with its check times and its rules,
// each rule with its trigger record. Enums are stored by name (entries are only appended); null values are left out.
// Times are kept exactly as given: firedAt is the local clock and dataAt the data source's, and neither is compared.
//
// What this version cannot read as a whole (another version, a missing switch or list, too many entries) throws, so
// that the caller never mistakes it for "no rules" and writes over the user's rules. A single invalid entry is dropped
// and the rest kept: a role that is not a valid subject (with its rules), or a rule of an invalid shape or an unknown
// mode. A rule of a metric outside the capability registry is kept: it may come from a newer version, and the
// evaluator reports it unsupported. An invalid record reads as empty, which only makes the rule start over, and an
// invalid check time as never; a role or rule listed twice keeps its first entry.
internal object ReminderJson {
    const val VERSION = 1

    // The longest event round an observation may carry (ReminderObservation).
    private const val MAX_ROUND = 120

    fun write(data: ReminderData): JSONObject {
        val rules = data.entries.groupBy { it.rule.subject }
        val subjects = rules.keys + data.checks.keys
        return JSONObject().put("version", VERSION).put("enabled", data.enabled)
            .put("subjects", JSONArray(subjects.map { subject ->
                val times = data.checks[subject] ?: ReminderCheckTimes()
                JSONObject().put("providerId", subject.providerId).put("accountId", subject.accountId)
                    .put("characterId", subject.characterId)
                    .put("lastCalibrationAt", times.lastCalibrationAt).put("lastRequestAt", times.lastRequestAt)
                    .put("rules", JSONArray(rules[subject].orEmpty().map(::writeEntry)))
            }))
    }

    private fun writeEntry(entry: ReminderEntry): JSONObject {
        val (rule, record) = entry
        return JSONObject().put("metricId", rule.metricId).put("mode", rule.mode.name).put("threshold", rule.threshold)
            .put("enabled", rule.enabled)
            .put("record", JSONObject().put("mode", record.mode?.name).put("threshold", record.threshold)
                .put("fired", record.fired).put("firedAt", record.firedAt).put("firedRound", record.firedRound)
                .put("dataAt", record.dataAt))
    }

    // Throws JSONException or IllegalArgumentException when the file cannot be read as a whole.
    fun read(value: JSONObject): ReminderData {
        require(value.getInt("version") == VERSION)
        val enabled = value.flag("enabled")
        val array = value.getJSONArray("subjects")
        require(array.length() <= ReminderStore.MAX_SUBJECTS)
        val items = (0 until array.length()).map(array::opt)
        val rules = items.sumOf { ((it as? JSONObject)?.opt("rules") as? JSONArray)?.length() ?: 0 }
        require(rules <= ReminderStore.MAX_RULES)
        val groups = items.mapNotNull(::readSubject).distinctBy { it.subject }
        val checks = groups.filter { it.times != ReminderCheckTimes() }.associate { it.subject to it.times }
        return ReminderData(enabled, groups.flatMap { it.entries }, checks)
    }

    private class Group(val subject: ReminderSubject, val times: ReminderCheckTimes, val entries: List<ReminderEntry>)

    private fun readSubject(item: Any?): Group? {
        val value = item as? JSONObject ?: return null
        val subject = try {
            ReminderSubject(value.text("providerId"), value.text("accountId"), value.text("characterId"))
        } catch (_: JSONException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val rules = value.opt("rules") as? JSONArray ?: return null
        val entries = (0 until rules.length()).mapNotNull { readEntry(subject, rules.opt(it)) }.distinctBy { it.rule.metricId }
        return Group(subject, ReminderCheckTimes(value.timeOrNull("lastCalibrationAt"), value.timeOrNull("lastRequestAt")),
            entries)
    }

    private fun readEntry(subject: ReminderSubject, item: Any?): ReminderEntry? {
        val value = item as? JSONObject ?: return null
        val rule = try {
            ReminderRule(subject, value.text("metricId"), ReminderMode.valueOf(value.text("mode")),
                value.optionalInt("threshold"), value.flag("enabled"))
        } catch (_: JSONException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return ReminderEntry(rule, readRecord(value.opt("record")))
    }

    private fun readRecord(item: Any?): TriggerRecord {
        val value = item as? JSONObject ?: return TriggerRecord()
        return try {
            val round = value.optionalText("firedRound")
            require(round == null || round.isNotBlank() && round.length <= MAX_ROUND)
            val firedAt = value.optionalLong("firedAt")
            val dataAt = value.optionalLong("dataAt")
            require((firedAt ?: 0) >= 0 && (dataAt ?: 0) >= 0)
            TriggerRecord(value.optionalText("mode")?.let(ReminderMode::valueOf), value.optionalInt("threshold"),
                value.flag("fired"), firedAt, round, dataAt)
        } catch (_: JSONException) {
            TriggerRecord()
        } catch (_: IllegalArgumentException) {
            TriggerRecord()
        }
    }

    // A check time that is missing or invalid reads as never, each on its own.
    private fun JSONObject.timeOrNull(key: String): Long? = try {
        optionalLong(key)?.takeIf { it >= 0 }
    } catch (_: IllegalArgumentException) {
        null
    }

    // Values must have their own JSON type: org.json would otherwise turn "80" or 80.5 into a threshold of 80.
    private fun JSONObject.present(key: String) = has(key) && !isNull(key)

    private fun JSONObject.text(key: String): String = get(key) as? String ?: throw IllegalArgumentException("Not a string")

    private fun JSONObject.optionalText(key: String): String? = if (present(key)) text(key) else null

    private fun JSONObject.flag(key: String): Boolean = get(key) as? Boolean ?: throw IllegalArgumentException("Not a boolean")

    private fun JSONObject.optionalLong(key: String): Long? {
        if (!present(key)) return null
        return when (val number = get(key)) {
            is Int -> number.toLong()
            is Long -> number
            else -> throw IllegalArgumentException("Not an integer")
        }
    }

    private fun JSONObject.optionalInt(key: String): Int? = optionalLong(key)?.let {
        require(it in Int.MIN_VALUE..Int.MAX_VALUE)
        it.toInt()
    }
}
