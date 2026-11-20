package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.StorageContract as Storage

import cafe.miafetta.tsuki.core.ReminderCheckTimes
import cafe.miafetta.tsuki.core.ReminderData
import cafe.miafetta.tsuki.core.ReminderEntry
import cafe.miafetta.tsuki.core.ReminderStore
import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TriggerRecord
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandAccount
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.providers.SklandRole
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// The checks behind the reminder operations of WidgetStorageProvider and the account cleanup in WidgetRuntimeAccounts:
// which roles may hold reminders, which rules are accepted, what the app process reads, and what an account change
// removes.
class ReminderAccessTest {
    @get:Rule val directory = TemporaryFolder()

    private val now = 1_793_956_830_000L
    private val cred = "test-credential-01"
    private val doctor = SklandRole("000000001", "测试博士 · 官服 · 000000001")
    private val endmin = SklandRole(SklandRole.endfieldId("1", "0000000011"), "测试管理员", SklandGame.ENDFIELD, "官服")
    private val account = SklandAccount("account-a", "", cred, listOf(doctor, endmin))
    private val other = SklandAccount("account-b", "别名", "other-credential", listOf(SklandRole("000000002", "另一位 · B服 · 000000002")))
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")

    @Test fun everyRoleOfAStoredAccountIsASubject() {
        val third = ReminderSubject(SklandArknightsAdapter.ID, "account-b", "000000002")
        assertEquals(setOf(arknights, endfield, third), ReminderAccess.subjects(listOf(account, other)))
        assertEquals(setOf(arknights), ReminderAccess.subjects("account-a", listOf(doctor)))
        assertEquals(emptySet<ReminderSubject>(), ReminderAccess.subjects(emptyList()))
    }

    @Test fun onlyOfferedRulesOfCurrentRolesAreAccepted() {
        val accounts = listOf(account, other)
        val accepted = listOf(
            ReminderRule(arknights, "arknights.sanity", PERCENT, 80), ReminderRule(arknights, "arknights.training", EVENT),
            ReminderRule(endfield, "endfield.sanity", VALUE, 200),
            ReminderRule(endfield, "endfield.pass_level", FULL, enabled = false),
        )
        for (rule in accepted) {
            assertTrue("$rule", ReminderAccess.accepts(rule, accounts))
        }
        val rejected = listOf(
            // Metrics outside the registry, and modes the metric does not offer.
            ReminderRule(arknights, "arknights.clues", VALUE, 3), ReminderRule(endfield, "endfield.seek_suspicion", FULL),
            ReminderRule(arknights, "arknights.recruit", PERCENT, 50),
            // Roles that are not on the account, or in another game.
            ReminderRule(arknights.copy(accountId = "account-b"), "arknights.sanity", FULL),
            ReminderRule(arknights.copy(characterId = "000000003"), "arknights.sanity", FULL),
            ReminderRule(endfield.copy(providerId = SklandArknightsAdapter.ID), "arknights.sanity", FULL),
            // A metric of the other game on a real role.
            ReminderRule(arknights, "endfield.sanity", FULL), ReminderRule(endfield, "arknights.sanity", FULL),
            // The debug fixture, even with the IDs of a real role.
            ReminderRule(arknights.copy(providerId = FakeArknightsAdapter.ID), "arknights.sanity", FULL),
            ReminderRule(ReminderSubject(FakeArknightsAdapter.ID, "demo-1", "amiya"), "arknights.sanity", FULL),
        )
        for (rule in rejected) {
            assertFalse("$rule", ReminderAccess.accepts(rule, accounts))
        }
        // A signed-out account's roles are no longer accepted.
        assertFalse(ReminderAccess.accepts(ReminderRule(arknights, "arknights.sanity", FULL), listOf(other)))
    }

    @Test fun theOverviewListsRulesWithoutRecordsOrCheckTimes() {
        val data = ReminderData(true, listOf(
            ReminderEntry(ReminderRule(arknights, "arknights.sanity", FULL), TriggerRecord(FULL, null, true, now, null, now)),
            ReminderEntry(ReminderRule(endfield, "endfield.sanity", PERCENT, 80, enabled = false)),
        ), mapOf(arknights to ReminderCheckTimes(now, now)))
        val overview = JSONObject(ReminderAccess.overview(data).toString())
        assertTrue(overview.getBoolean(Storage.ENABLED))
        val rules = overview.getJSONArray(Storage.RULES)
        assertEquals(2, rules.length())
        val first = rules.getJSONObject(0)
        assertEquals(setOf(Storage.PROVIDER_ID, Storage.ACCOUNT_ID, Storage.CHARACTER_ID, Storage.METRIC_ID, Storage.MODE,
            Storage.ENABLED), first.keys().asSequence().toSet())
        assertEquals(listOf(SklandArknightsAdapter.ID, "account-a", "000000001", "arknights.sanity", "FULL"),
            listOf(Storage.PROVIDER_ID, Storage.ACCOUNT_ID, Storage.CHARACTER_ID, Storage.METRIC_ID, Storage.MODE).map(first::getString))
        val second = rules.getJSONObject(1)
        assertEquals(80, second.getInt(Storage.THRESHOLD))
        assertFalse(second.getBoolean(Storage.ENABLED))
        assertEquals(setOf(Storage.ENABLED, Storage.RULES), overview.keys().asSequence().toSet())
        val empty = ReminderAccess.overview(ReminderData())
        assertFalse(empty.getBoolean(Storage.ENABLED))
        assertEquals(0, empty.getJSONArray(Storage.RULES).length())
    }

    @Test fun accountChangesRemoveTheRulesOfRolesThatAreGone() {
        val store = ReminderStore(File(directory.root, "reminders/v1.json"))
        val third = ReminderSubject(SklandArknightsAdapter.ID, "account-b", "000000002")
        store.setEnabled(true)
        for (subject in listOf(arknights, endfield, third)) {
            val rule = ReminderRule(subject, if (subject == endfield) "endfield.sanity" else "arknights.sanity", FULL)
            store.save(rule)
            store.writeRecords(mapOf(ReminderEntry(rule) to TriggerRecord(FULL, null, true, now, null, now)))
            store.writeChecks(subject, ReminderCheckTimes(now, now))
        }
        val before = store.read()
        // Updating the Cred of account-a: its Endfield role is gone, its Arknights role stays with its record.
        val updated = SklandAccount(account.id, account.name, "renewed-credential", listOf(doctor))
        assertEquals(setOf(endfield), store.retainRoles(updated.id, ReminderAccess.subjects(updated.id, updated.roles)))
        assertEquals(before.entries.filter { it.rule.subject != endfield }, store.read().entries)
        assertEquals(before.checks - endfield, store.read().checks)
        // The same roles again remove nothing.
        assertEquals(emptySet<ReminderSubject>(), store.retainRoles(updated.id, ReminderAccess.subjects(updated.id, updated.roles)))
        // Signing out account-a leaves account-b alone.
        assertEquals(setOf(arknights), store.deleteAccount(account.id))
        assertEquals(setOf(third), store.read().subjects)
        // Resetting the accounts keeps the master switch only.
        store.clear()
        assertEquals(ReminderData(enabled = true), store.read())
    }
}
