package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.*
import org.junit.Test

// The role groups of the reminder settings page: their titles, telling same-named roles apart, and their order.
class ReminderRolesTest {
    private val doctor = UiRole("a", "", "000000001", "测试博士 · 官服 · 000000001")
    private val admin = UiRole("a", "", "1~000000011", "测试管理员", SklandGame.ENDFIELD.providerId, "官服")
    private val fake = UiRole("debug", "", "999", "模拟博士 · 官服 · 999", FakeArknightsAdapter.ID)

    @Test fun labelsNameTheRoleAndItsGame() {
        val options = ReminderRoles.options(listOf(doctor, admin))
        assertEquals(listOf("测试博士（明日方舟）", "测试管理员（终末地）"), options.map { it.label })
        assertEquals(listOf(SklandGame.ARKNIGHTS, SklandGame.ENDFIELD), options.map { it.game })
        assertEquals(listOf(doctor.key, admin.key), options.map { it.key })
        // The legacy Arknights label carries "·" as a delimiter; it never reaches a group title.
        assertTrue(options.none { "·" in it.label })
    }

    @Test fun sameNamedRolesGetServerAndUid() {
        val twin = UiRole("b", "", "000000002", "测试博士 · B服 · 000000002")
        val sameNameOtherGame = UiRole("a", "", "1~000000012", "测试博士", SklandGame.ENDFIELD.providerId, "官服")
        val options = ReminderRoles.options(listOf(doctor, twin, sameNameOtherGame))
        assertEquals(listOf("测试博士（明日方舟）官服 UID 000000001", "测试博士（明日方舟）B服 UID 000000002", "测试博士（终末地）"),
            options.map { it.label })
        assertTrue(options.none { "·" in it.label })
    }

    @Test fun theDebugFixtureIsNotListed() {
        assertEquals(listOf(doctor.key), ReminderRoles.options(listOf(fake, doctor)).map { it.key })
        assertTrue(ReminderRoles.options(listOf(fake)).isEmpty())
    }

    // Arknights groups first, then Endfield; within a game the order of the accounts and their roles is kept.
    @Test fun groupsGoGameByGameInListOrder() {
        val second = UiRole("b", "", "1~000000021", "第二管理员", SklandGame.ENDFIELD.providerId, "官服")
        val twin = UiRole("b", "", "000000002", "第二博士 · B服 · 000000002")
        val options = ReminderRoles.options(listOf(admin, doctor, fake, second, twin))
        assertEquals(listOf(doctor.key, twin.key, admin.key, second.key), options.map { it.key })
        assertEquals(listOf("测试博士（明日方舟）", "第二博士（明日方舟）", "测试管理员（终末地）", "第二管理员（终末地）"),
            options.map { it.label })
    }

    @Test fun withoutRolesNothingIsListed() {
        assertTrue(ReminderRoles.options(emptyList()).isEmpty())
    }

    @Test fun aRolesSubjectUsesItsDataSourceAccountAndId() {
        assertEquals(ReminderSubject(SklandArknightsAdapter.ID, "a", "000000001"), doctor.reminderSubject())
        assertEquals(ReminderSubject(SklandGame.ENDFIELD.providerId, "a", "1~000000011"), admin.reminderSubject())
    }
}
