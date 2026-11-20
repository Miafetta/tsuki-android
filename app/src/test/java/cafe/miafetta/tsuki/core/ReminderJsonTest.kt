package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// The reminder file format: every field round-trips, the version 1 text stays readable, single invalid entries are
// dropped and files this version cannot read as a whole are rejected.
class ReminderJsonTest {
    private val now = 1_793_956_830_000L
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")
    private val second = ReminderSubject(SklandArknightsAdapter.ID, "account-b", "000000002")

    // Every mode of rules and records, a record without a mode, disabled and unregistered rules, a record kept for
    // another threshold, and check times with either value missing.
    private val sample = ReminderData(true, listOf(
        ReminderEntry(ReminderRule(arknights, "arknights.sanity", FULL), TriggerRecord(FULL, fired = true, firedAt = now,
            dataAt = now - 30_000)),
        ReminderEntry(ReminderRule(arknights, "arknights.drones", PERCENT, 80), TriggerRecord(PERCENT, 80, dataAt = 0)),
        ReminderEntry(ReminderRule(arknights, "arknights.training", EVENT),
            TriggerRecord(EVENT, null, true, now, "char_002_amiya/2", now - 30_000)),
        ReminderEntry(ReminderRule(arknights, "arknights.manufacture", VALUE, 30, enabled = false)),
        ReminderEntry(ReminderRule(endfield, "endfield.sanity", VALUE, 200), TriggerRecord(VALUE, 150, firedAt = now)),
        ReminderEntry(ReminderRule(endfield, "endfield.future_metric", FULL)),
    ), mapOf(arknights to ReminderCheckTimes(now - 3_600_000, now - 60_000), endfield to ReminderCheckTimes(now - 1),
        second to ReminderCheckTimes(lastRequestAt = now)))

    @Test fun everyFieldRoundTrips() {
        assertEquals(sample, reread(ReminderJson.write(sample)))
        assertEquals(ReminderData(), reread(ReminderJson.write(ReminderData())))
        // Null values are left out rather than written as JSON null.
        val text = ReminderJson.write(sample).toString()
        assertFalse(text, "null" in text)
    }

    @Test fun theVersionOneTextStaysReadable() {
        val text = """
            {"version": 1, "enabled": true, "subjects": [
              {"providerId": "skland", "accountId": "account-a", "characterId": "000000001",
               "lastCalibrationAt": 1793950000000, "lastRequestAt": 1793956000000, "rules": [
                {"metricId": "arknights.sanity", "mode": "PERCENT", "threshold": 80, "enabled": true,
                 "record": {"mode": "PERCENT", "threshold": 80, "fired": true, "firedAt": 1793956830000,
                  "dataAt": 1793956800000}},
                {"metricId": "arknights.training", "mode": "EVENT", "enabled": true,
                 "record": {"mode": "EVENT", "fired": true, "firedAt": 1793956830000, "firedRound": "char_002_amiya/2",
                  "dataAt": 1793956800000}},
                {"metricId": "arknights.manufacture", "mode": "VALUE", "threshold": 30, "enabled": false,
                 "record": {"fired": false}}]},
              {"providerId": "skland.endfield", "accountId": "account-a", "characterId": "1~0000000011", "rules": [
                {"metricId": "endfield.sanity", "mode": "FULL", "enabled": true, "record": {"fired": false}}]}]}
        """
        val expected = ReminderData(true, listOf(
            ReminderEntry(ReminderRule(arknights, "arknights.sanity", PERCENT, 80),
                TriggerRecord(PERCENT, 80, true, 1_793_956_830_000, dataAt = 1_793_956_800_000)),
            ReminderEntry(ReminderRule(arknights, "arknights.training", EVENT),
                TriggerRecord(EVENT, null, true, 1_793_956_830_000, "char_002_amiya/2", 1_793_956_800_000)),
            ReminderEntry(ReminderRule(arknights, "arknights.manufacture", VALUE, 30, enabled = false)),
            ReminderEntry(ReminderRule(endfield, "endfield.sanity", FULL)),
        ), mapOf(arknights to ReminderCheckTimes(1_793_950_000_000, 1_793_956_000_000)))
        assertEquals(expected, ReminderJson.read(JSONObject(text)))
        assertEquals(expected, reread(ReminderJson.write(expected)))
    }

    @Test fun invalidRulesAreDroppedOneByOne() {
        val file = ReminderJson.write(sample)
        val rules = file.getJSONArray("subjects").getJSONObject(0).getJSONArray("rules")
        rules.put(rule("arknights.recruit", "FULL").put("threshold", 1))
        rules.put(rule("arknights.recruit", "PERCENT").put("threshold", 150))
        rules.put(rule("arknights.recruit", "SOMETIMES"))
        rules.put(rule("arknights.recruit", "VALUE").put("threshold", "80"))
        rules.put(rule("arknights.recruit", "FULL").put("enabled", "true"))
        rules.put(rule(" ", "FULL"))
        rules.put(rule("arknights.recruit", "FULL").apply { remove("metricId") })
        rules.put(42)
        // A rule listed twice keeps its first entry.
        rules.put(rule("arknights.sanity", "VALUE").put("threshold", 100))
        assertEquals(sample, reread(file))
    }

    @Test fun invalidRecordsAndTimesReadAsEmpty() {
        val file = ReminderJson.write(sample)
        val rules = file.getJSONArray("subjects").getJSONObject(0).getJSONArray("rules")
        val broken = listOf(JSONObject().put("mode", "LATER").put("fired", true), JSONObject().put("fired", "yes"),
            JSONObject().put("fired", true).put("firedAt", -1), JSONObject().put("fired", true).put("dataAt", 1.5),
            JSONObject().put("fired", true).put("firedRound", " "), JSONObject().put("fired", true).put("firedRound", "r".repeat(121)),
            JSONObject().put("mode", "FULL"), JSONObject().put("fired", true).put("threshold", 3_000_000_000L))
        val metrics = broken.indices.map { "arknights.future_$it" }
        broken.forEachIndexed { index, record -> rules.put(rule(metrics[index], "FULL").put("record", record)) }
        rules.put(rule("arknights.recruit", "FULL").put("record", "fired"))
        val times = file.getJSONArray("subjects").getJSONObject(0)
        times.put("lastCalibrationAt", "soon").put("lastRequestAt", -60_000)
        val second = file.getJSONArray("subjects").getJSONObject(2)
        second.put("lastCalibrationAt", 12.5)
        val added = (metrics + "arknights.recruit").map { ReminderEntry(ReminderRule(arknights, it, FULL)) }
        val entries = sample.entries.take(4) + added + sample.entries.drop(4)
        // Both times of the first role are invalid, so it has none; the third keeps its valid request time.
        assertEquals(sample.copy(entries = entries, checks = sample.checks - arknights), reread(file))
    }

    @Test fun invalidRolesAreDroppedWithTheirRules() {
        val file = ReminderJson.write(sample)
        val subjects = file.getJSONArray("subjects")
        val rules = JSONArray().put(rule("arknights.sanity", "FULL"))
        subjects.put(subject("skland", "", "000000003").put("rules", rules))
        subjects.put(subject("skland", "account-c", "x".repeat(81)).put("rules", rules))
        subjects.put(subject("skland", "account-c", "000000003"))
        subjects.put(subject("skland", "account-c", "000000003").put("rules", JSONObject()))
        subjects.put("skland")
        // A role listed twice keeps its first entry, including its check times.
        subjects.put(subject(arknights).put("lastRequestAt", now + 1).put("rules", rules))
        assertEquals(sample, reread(file))
    }

    @Test fun filesThisVersionCannotReadAreRejected() {
        val file = ReminderJson.write(sample).toString()
        val oneRule = JSONArray().put(rule("arknights.sanity", "FULL"))
        val tooManyRoles = JSONArray((0..ReminderStore.MAX_SUBJECTS).map { subject("skland", "account-$it", "1").put("rules", oneRule) })
        val tooManyRules = JSONArray().put(subject(arknights).put("rules",
            JSONArray((0..ReminderStore.MAX_RULES).map { rule("metric.m$it", "FULL") })))
        val broken = listOf(JSONObject(file).put("version", 2), JSONObject(file).apply { remove("version") },
            JSONObject(file).apply { remove("enabled") }, JSONObject(file).put("enabled", "true"),
            JSONObject(file).apply { remove("subjects") }, JSONObject(file).put("subjects", JSONObject()),
            JSONObject(file).put("subjects", tooManyRoles), JSONObject(file).put("subjects", tooManyRules))
        for (value in broken) {
            val failure = runCatching { ReminderJson.read(value) }.exceptionOrNull()
            assertTrue("$failure", failure is IllegalArgumentException || failure is JSONException)
        }
        // The limits themselves are readable.
        val limit = JSONArray().put(subject(arknights).put("rules",
            JSONArray((1..ReminderStore.MAX_RULES).map { rule("metric.m$it", "FULL") })))
        assertEquals(ReminderStore.MAX_RULES, ReminderJson.read(JSONObject(file).put("subjects", limit)).entries.size)
    }

    private fun reread(value: JSONObject) = ReminderJson.read(JSONObject(value.toString()))

    private fun rule(metricId: String, mode: String) = JSONObject().put("metricId", metricId).put("mode", mode)
        .put("enabled", true)

    private fun subject(providerId: String, accountId: String, characterId: String) = JSONObject()
        .put("providerId", providerId).put("accountId", accountId).put("characterId", characterId)

    private fun subject(value: ReminderSubject) = subject(value.providerId, value.accountId, value.characterId)
}
