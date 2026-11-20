package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.*
import org.junit.Test

// The accounts of "通知提醒": the role groups of an account's page and their order, and the summary of its row.
class ReminderAccountsTest {
    private val admin = UiRole("a", "", "1~000000011", "测试管理员", SklandGame.ENDFIELD.providerId, "官服")
    private val doctor = UiRole("a", "", "000000001", "测试博士 · 官服 · 000000001")
    private val twin = UiRole("a", "", "000000002", "测试博士 · B服 · 000000002")
    private val other = UiRole("b", "", "000000003", "第二博士 · 官服 · 000000003")
    private val fake = UiRole("a", "", "999", "模拟博士 · 官服 · 999", FakeArknightsAdapter.ID)
    private val account = UiAccount("a", "主账号", listOf(admin, doctor, fake, twin))
    private val second = UiAccount("b", "", listOf(other))

    private fun rule(role: UiRole, metricId: String, enabled: Boolean = true) =
        ReminderRule(role.reminderSubject(), metricId, if (metricId == "arknights.training") EVENT else FULL, enabled = enabled)

    // Arknights roles first, then Endfield ones, each in the account's order; the fixture is never listed. Same-named
    // roles of the account get their server and UID, as on the reminder groups of every page.
    @Test fun anAccountPageListsItsRolesGameByGame() {
        val options = ReminderAccounts.options(account)
        assertEquals(listOf(doctor.key, twin.key, admin.key), options.map { it.key })
        assertEquals(listOf("测试博士（明日方舟）官服 UID 000000001", "测试博士（明日方舟）B服 UID 000000002", "测试管理员（终末地）"),
            options.map { it.label })
        assertEquals(listOf(SklandGame.ARKNIGHTS, SklandGame.ARKNIGHTS, SklandGame.ENDFIELD), options.map { it.game })
        // Another account's role of the same name does not count as a twin.
        assertEquals(listOf("第二博士（明日方舟）"), ReminderAccounts.options(second).map { it.label })
        assertTrue(ReminderAccounts.options(UiAccount("c", "", listOf(fake))).isEmpty())
    }

    // The row counts the enabled rules of the account's own roles, for the metrics their pages list.
    @Test fun theRowSaysHowManyRemindersAreOn() {
        assertEquals(ReminderAccounts.NONE_ON, ReminderAccounts.summary(account, UiReminders()))
        val reminders = UiReminders(false, listOf(rule(doctor, "arknights.sanity"), rule(twin, "arknights.training"),
            rule(admin, "endfield.sanity"), rule(doctor, "arknights.drones", enabled = false),
            rule(other, "arknights.sanity"), rule(fake, "arknights.sanity"),
            ReminderRule(doctor.reminderSubject(), "arknights.trading", PERCENT, 80)))
        assertEquals(4, ReminderAccounts.enabled(account, reminders))
        assertEquals("已开启 4 项提醒", ReminderAccounts.summary(account, reminders))
        assertEquals("已开启 1 项提醒", ReminderAccounts.summary(second, reminders))
        assertEquals(ReminderAccounts.NO_ROLES, ReminderAccounts.summary(UiAccount("c", "", emptyList()), reminders))
    }

    @Test fun noTextUsesTheMiddleDot() {
        val reminders = UiReminders(true, listOf(rule(doctor, "arknights.sanity")))
        val texts = listOf(ReminderAccounts.summary(account, reminders), ReminderAccounts.summary(account, UiReminders()),
            ReminderAccounts.NO_ROLES, ReminderAccounts.NO_ROLES_PAGE, ReminderAccounts.NONE_ON) +
            ReminderAccounts.options(account).map { it.label }
        assertTrue(texts.none { "·" in it })
    }
}
