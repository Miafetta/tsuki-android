package cafe.miafetta.tsuki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DashboardTimeTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-10-30T15:30:00Z").toEpochMilli()
    @Test fun recoveryClockUsesCalendarDaysAndHandlesUnknown() {
        assertEquals("未知", recoveryClock(null, now, zone))
        assertEquals("未知", recoveryClock(0, now, zone))
        assertEquals("今天 23:31", recoveryClock(now + 60_000, now, zone))
        assertEquals("明天 00:30", recoveryClock(now + 3_600_000, now, zone))
        assertEquals("11-01 23:30", recoveryClock(now + 2 * 86_400_000, now, zone))
        assertEquals("今天 22:30", recoveryClock(now - 3_600_000, now, zone))
    }
    @Test fun syncAgeHandlesBoundaryFutureAndUnknownTimestamps() {
        assertEquals("时间未知", syncAge(null, now, zone))
        assertEquals("时间未知", syncAge(-1, now, zone))
        assertEquals("刚刚", syncAge(now + 60_000, now, zone))
        assertEquals("刚刚", syncAge(now - 59_999, now, zone))
        assertEquals("1 分钟前", syncAge(now - 60_000, now, zone))
        assertEquals("59 分钟前", syncAge(now - 3_599_999, now, zone))
        assertEquals("10-30 22:30", syncAge(now - 3_600_000, now, zone))
    }
}
