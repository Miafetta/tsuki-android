package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderNotice
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.providers.SklandRole
import org.junit.Assert.*
import org.junit.Test

// What a reminder notification shows: its channel, its title without "·", and tags that keep rules apart.
class ReminderNotificationsTest {
    private val doctor = SklandRole("000000001", "测试博士 · 官服 · 000000001")
    private val endmin = SklandRole(SklandRole.endfieldId("1", "0000000011"), "测试管理员", SklandGame.ENDFIELD, "官服")
    private val arknights = ReminderAccess.subject("account-a", doctor)
    private val endfield = ReminderAccess.subject("account-a", endmin)

    @Test fun eachGameHasItsOwnChannel() {
        assertEquals("reminder.skland", ReminderNotifications.channelId(SklandGame.ARKNIGHTS))
        assertEquals("reminder.skland.endfield", ReminderNotifications.channelId(SklandGame.ENDFIELD))
    }

    @Test fun titlesNameTheRoleAndTheGameWithoutTheLabelDelimiter() {
        assertEquals("测试博士（明日方舟）", ReminderNotifications.title(doctor))
        assertEquals("测试管理员（终末地）", ReminderNotifications.title(endmin))
        // A role whose name part is empty falls back to its UID rather than an empty name.
        assertEquals("000000002（明日方舟）", ReminderNotifications.title(SklandRole("000000002", " · B服 · 000000002")))
        for (role in listOf(doctor, endmin)) assertFalse('·' in ReminderNotifications.title(role))
    }

    @Test fun tagsTellRolesAndMetricsApartAndStayTheSameForARule() {
        val other = ReminderSubject(SklandArknightsAdapter.ID, "account-b", "000000001")
        val tags = listOf(arknights to "arknights.sanity", arknights to "arknights.drones", endfield to "endfield.sanity",
            other to "arknights.sanity").map { (subject, metricId) -> ReminderNotifications.tag(subject, metricId) }
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(tags.first(), ReminderNotifications.tag(arknights.copy(), "arknights.sanity"))
    }

    @Test fun aPostCarriesTheNoticeAndItsRole() {
        val notice = ReminderNotice(endfield, "endfield.sanity", ReminderMode.VALUE, 200, "理智已达到 200", estimated = false)
        val post = ReminderNotifications.post(notice, endmin)
        assertEquals(ReminderPost(endfield, SklandGame.ENDFIELD, "reminder.skland.endfield",
            ReminderNotifications.tag(endfield, "endfield.sanity"), "测试管理员（终末地）", "理智已达到 200"), post)
    }
}
