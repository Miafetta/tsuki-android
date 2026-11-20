package cafe.miafetta.tsuki.ui.state

import android.app.NotificationManager
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.*
import org.junit.Test

// The status rows of the reminder settings page.
class ReminderProblemTest {
    private val off = NotificationManager.IMPORTANCE_NONE
    private val on = NotificationManager.IMPORTANCE_DEFAULT

    @Test fun nothingIsReportedWhenAllIsWell() {
        val status = ReminderSystemStatus(true, mapOf(SklandGame.ARKNIGHTS to on, SklandGame.ENDFIELD to on))
        assertTrue(ReminderProblem.of(true, status).isEmpty())
    }

    @Test fun notificationsOffIsOneRow() {
        val problems = ReminderProblem.of(true, ReminderSystemStatus(false))
        assertEquals(listOf(ReminderProblem.NotificationsOff), problems)
        assertEquals("通知权限未开启", problems.single().title)
    }

    @Test fun eachClosedChannelIsARowAndAChannelNotCreatedYetIsNotClosed() {
        val problems = ReminderProblem.of(true, ReminderSystemStatus(true, mapOf(SklandGame.ARKNIGHTS to null,
            SklandGame.ENDFIELD to off)))
        assertEquals(listOf(ReminderProblem.ChannelOff(SklandGame.ENDFIELD)), problems)
        assertEquals("终末地的通知已关闭", problems.single().title)
        assertTrue(ReminderProblem.of(true, ReminderSystemStatus(true, emptyMap())).isEmpty())
        val both = ReminderProblem.of(true, ReminderSystemStatus(false, mapOf(SklandGame.ARKNIGHTS to off,
            SklandGame.ENDFIELD to off)))
        assertEquals(listOf("通知权限未开启", "明日方舟的通知已关闭", "终末地的通知已关闭"), both.map { it.title })
    }

    @Test fun nothingIsShownWhileRemindersAreOff() {
        val status = ReminderSystemStatus(false, mapOf(SklandGame.ARKNIGHTS to off, SklandGame.ENDFIELD to off))
        assertTrue(ReminderProblem.of(false, status).isEmpty())
    }
}
