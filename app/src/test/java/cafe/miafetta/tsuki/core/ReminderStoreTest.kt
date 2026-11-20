package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Reminder storage: what survives a restart, how saving, the master switch and judged records change the records,
// cleanup by account and role, and damaged or oversized files.
class ReminderStoreTest {
    @get:Rule val directory = TemporaryFolder()

    private val now = 1_793_956_830_000L
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")
    private val second = ReminderSubject(SklandArknightsAdapter.ID, "account-b", "000000001")
    private val file get() = File(directory.root, "reminders/v1.json")

    private fun store() = ReminderStore(file)
    private fun rule(subject: ReminderSubject, metricId: String, mode: ReminderMode, threshold: Int? = null,
        enabled: Boolean = true) = ReminderRule(subject, metricId, mode, threshold, enabled)
    private fun fired(rule: ReminderRule, round: String? = null) =
        TriggerRecord(rule.mode, rule.threshold, true, now, round, now - 30_000)

    @Test fun aMissingFileIsOffWithoutRulesAndStaysMissingUntilSomethingChanges() {
        val store = store()
        assertEquals(ReminderData(), store.read())
        assertEquals(ReminderCheckTimes(), store.checks(arknights))
        store.setEnabled(false)
        store.delete(arknights, "arknights.sanity")
        assertEquals(emptySet<ReminderSubject>(), store.deleteAccount("account-a"))
        assertEquals(emptySet<ReminderSubject>(), store.retainRoles("account-a", emptySet()))
        store.clear()
        assertEquals(0, store.writeRecords(emptyMap()))
        assertFalse(file.exists())
    }

    @Test fun rulesRecordsAndCheckTimesSurviveARestart() {
        val store = store()
        val rules = listOf(rule(arknights, "arknights.sanity", FULL), rule(arknights, "arknights.drones", PERCENT, 80),
            rule(arknights, "arknights.training", EVENT), rule(endfield, "endfield.sanity", VALUE, 200))
        store.setEnabled(true)
        rules.forEach(store::save)
        val records = rules.associate { ReminderEntry(it) to fired(it, if (it.mode == EVENT) "char_002_amiya/2" else null) }
        assertEquals(rules.size, store.writeRecords(records))
        store.writeChecks(arknights, ReminderCheckTimes(now - 3_600_000, now - 60_000))
        store.writeChecks(endfield, ReminderCheckTimes(lastRequestAt = now))
        val expected = ReminderData(true, records.map { (entry, record) -> entry.copy(record = record) },
            mapOf(arknights to ReminderCheckTimes(now - 3_600_000, now - 60_000), endfield to ReminderCheckTimes(lastRequestAt = now)))
        // A new instance over the same folder is what the :widgetProvider process sees after being restarted.
        assertEquals(expected, store().read())
        assertEquals(ReminderCheckTimes(lastRequestAt = now), store().checks(endfield))
        store.writeChecks(endfield, ReminderCheckTimes())
        assertEquals(expected.checks - endfield, store().read().checks)
    }

    @Test fun aRoleHasOneRulePerMetric() {
        val store = store()
        store.save(rule(arknights, "arknights.sanity", FULL))
        store.save(rule(arknights, "arknights.sanity", PERCENT, 80))
        store.save(rule(arknights, "arknights.drones", FULL))
        // The same character ID on another account or in another game is another role.
        store.save(rule(second, "arknights.sanity", FULL))
        store.save(rule(endfield.copy(characterId = arknights.characterId), "arknights.sanity", FULL))
        val entries = store().read().entries
        assertEquals(4, entries.size)
        assertEquals(rule(arknights, "arknights.sanity", PERCENT, 80), store().read().entry(arknights, "arknights.sanity")!!.rule)
        store.delete(arknights, "arknights.sanity")
        assertNull(store().read().entry(arknights, "arknights.sanity"))
        assertEquals(3, store().read().entries.size)
    }

    @Test fun disablingStoresTheEmptyRecordAndEditingKeepsTheRecord() {
        val store = store()
        val eighty = rule(arknights, "arknights.drones", PERCENT, 80)
        store.save(eighty)
        store.writeRecords(mapOf(ReminderEntry(eighty) to fired(eighty)))
        // A changed threshold keeps the record; the evaluator starts it over because it was kept for another rule.
        val fifty = eighty.copy(threshold = 50)
        val edited = store.save(fifty).entry(arknights, "arknights.drones")!!
        assertEquals(ReminderEntry(fifty, fired(eighty)), edited)
        assertFalse(edited.record.keptFor(fifty))
        assertEquals(ReminderEntry(fifty.copy(enabled = false)),
            store.save(fifty.copy(enabled = false)).entry(arknights, "arknights.drones"))
        assertEquals(ReminderEntry(fifty), store.save(fifty).entry(arknights, "arknights.drones"))
    }

    @Test fun turningTheMasterSwitchOnStartsEveryRuleOver() {
        val store = store()
        val sanity = rule(arknights, "arknights.sanity", FULL)
        store.save(sanity)
        store.writeChecks(arknights, ReminderCheckTimes(now, now))
        store.setEnabled(true)
        store.writeRecords(mapOf(ReminderEntry(sanity) to fired(sanity)))
        // On while on, and off, keep the records; only off to on resets them. Check times are never reset.
        assertEquals(fired(sanity), store.setEnabled(true).entry(arknights, sanity.metricId)!!.record)
        val off = store.setEnabled(false)
        assertFalse(off.enabled)
        assertEquals(fired(sanity), off.entry(arknights, sanity.metricId)!!.record)
        val on = store.setEnabled(true)
        assertEquals(ReminderData(true, listOf(ReminderEntry(sanity)), mapOf(arknights to ReminderCheckTimes(now, now))), on)
        assertEquals(on, store().read())
    }

    @Test fun judgedRecordsAreWrittenOnlyOverTheStateTheyWereJudgedFrom() {
        val store = store()
        val sanity = rule(arknights, "arknights.sanity", FULL)
        val drones = rule(arknights, "arknights.drones", VALUE, 150)
        store.save(sanity)
        store.save(drones)
        val judgedFrom = store.read().entries
        // Meanwhile the drone rule is edited and the sanity rule's record is replaced.
        store.save(drones.copy(threshold = 120))
        store.writeRecords(mapOf(ReminderEntry(sanity) to fired(sanity, "other")))
        val written = store.writeRecords(judgedFrom.associateWith { fired(it.rule) })
        assertEquals(0, written)
        assertEquals(listOf(ReminderEntry(sanity, fired(sanity, "other")), ReminderEntry(drones.copy(threshold = 120))),
            store().read().entries)
        // A judgement of a removed rule does not bring it back.
        store.delete(arknights, sanity.metricId)
        assertEquals(0, store.writeRecords(mapOf(ReminderEntry(sanity, fired(sanity, "other")) to TriggerRecord())))
        assertNull(store().read().entry(arknights, sanity.metricId))
    }

    @Test fun cleanupRemovesRulesAndCheckTimesByAccountAndByRole() {
        val store = store()
        store.setEnabled(true)
        listOf(arknights, endfield, second).forEach { subject ->
            store.save(rule(subject, "test.metric", FULL))
            store.writeChecks(subject, ReminderCheckTimes(now, now))
        }
        // A role without rules keeps its check times until its account or the role goes.
        val idle = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000009")
        store.writeChecks(idle, ReminderCheckTimes(lastRequestAt = now))
        assertEquals(setOf(endfield, idle), store.retainRoles("account-a", setOf(arknights, second)))
        assertEquals(setOf(arknights, second), store().read().subjects)
        assertEquals(setOf(arknights, second), store().read().checks.keys)
        assertEquals(setOf(arknights), store.deleteAccount("account-a"))
        assertEquals(setOf(second), store().read().subjects)
        store.clear()
        assertEquals(ReminderData(enabled = true), store().read())
    }

    @Test fun unreadableFilesFailEveryOperationAndAreLeftAsTheyAre() {
        val valid = ReminderJson.write(ReminderData(true, listOf(ReminderEntry(rule(arknights, "arknights.sanity", FULL)))))
        val contents = listOf("not json", "[]", "", JSONObject(valid.toString()).put("version", 2).toString(),
            valid.toString() + " ".repeat(ReminderStore.MAX_BYTES))
        for (content in contents) {
            file.parentFile!!.mkdirs()
            file.writeText(content)
            val store = store()
            val operations = listOf({ store.read() }, { store.save(rule(arknights, "arknights.drones", FULL)) },
                { store.delete(arknights, "arknights.sanity") }, { store.setEnabled(false) },
                { store.writeRecords(emptyMap()) }, { store.writeChecks(arknights, ReminderCheckTimes(now)) },
                { store.deleteAccount("account-a") }, { store.retainRoles("account-a", emptySet()) })
            for (operation in operations) {
                val failure = assertThrows(DataSourceFailure::class.java) { operation() }
                assertEquals(RefreshFailure.STORAGE, failure.reason)
                assertEquals(content, file.readText())
            }
            // Only clearing, which resetting the accounts asks for, replaces it.
            store.clear()
            assertEquals(ReminderData(), store().read())
        }
    }

    @Test fun writesThatReadingWouldRejectAreRefused() {
        val store = store()
        val limit = (1..ReminderStore.MAX_RULES).map { ReminderEntry(rule(arknights, "test.metric_$it", FULL)) }
        file.parentFile!!.mkdirs()
        file.writeText(ReminderJson.write(ReminderData(true, limit)).toString())
        assertThrows(IllegalArgumentException::class.java) { store.save(rule(arknights, "test.extra", FULL)) }
        assertThrows(IllegalArgumentException::class.java) { store.save(rule(second, "test.extra", FULL)) }
        assertEquals(ReminderStore.MAX_RULES, store().read().entries.size)
        // Rules already stored can still be changed at the limit.
        store.save(rule(arknights, "test.metric_1", PERCENT, 50))
        assertEquals(PERCENT, store().read().entries.first().rule.mode)
        // Long IDs and rounds reach the byte limit before the rule limit; the file stays readable.
        val long = ReminderSubject(SklandGame.ENDFIELD.providerId, "a".repeat(80), "1~" + "0".repeat(40))
        val rules = (1..400).map { rule(long, "m".repeat(76) + "$it".padStart(4, '0'), FULL) }
        file.writeText(ReminderJson.write(ReminderData(true, rules.map(::ReminderEntry))).toString())
        assertTrue(file.length() < ReminderStore.MAX_BYTES)
        val records = store.read().entries.associateWith { fired(it.rule, "r".repeat(120)) }
        assertThrows(IllegalArgumentException::class.java) { store.writeRecords(records) }
        assertEquals(rules, store().read().entries.map { it.rule })
    }
}
