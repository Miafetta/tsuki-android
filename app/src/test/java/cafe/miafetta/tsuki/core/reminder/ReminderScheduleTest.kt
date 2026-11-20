package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.reminder.ReminderMode.EVENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import org.junit.Assert.*
import org.junit.Test

// Expected times of single rules and the next check of a role.
class ReminderScheduleTest : ReminderFixture() {
    @Test fun arknightsSanityWorksBackFromTheFullTimeAt360Seconds() {
        val sanity = "arknights.sanity"
        val fullAt = now + 35 * 360 * second
        val observation = observe(sanity, 100, 135, fullAt = fullAt)
        assertEquals(fullAt, expected(rule(sanity, FULL), observation))
        assertEquals(fullAt - 27 * 360 * second, expected(rule(sanity, PERCENT, 80), observation))
        assertEquals(fullAt - 15 * 360 * second, expected(rule(sanity, VALUE, 120), observation))
        assertEquals(fullAt, expected(rule(sanity, VALUE, 135), observation))
        // Met already, or met by known times: nothing to plan.
        assertNull(expected(rule(sanity, VALUE, 100), observation))
        assertNull(expected(rule(sanity, VALUE, 120), observation, at = fullAt - 15 * 360 * second))
        assertNull(expected(rule(sanity, VALUE, 140), observation))
        assertNull(expected(rule(sanity, FULL), observation.copy(fullAt = null)))
        assertNull(expected(rule(sanity, FULL, enabled = false), observation))
    }

    @Test fun dronesWorkBackAlongTheLinearRecovery() {
        val drones = "arknights.drones"
        val fullAt = now + 10_000 * second
        val observation = observe(drones, 100, 200, fullAt = fullAt)
        assertEquals(fullAt, expected(rule(drones, FULL), observation))
        assertEquals(now + 5_000 * second, expected(rule(drones, VALUE, 150), observation))
        assertEquals(now + 5_000 * second, expected(rule(drones, PERCENT, 75), observation))
        assertNull(expected(rule(drones, VALUE, 100), observation))
        // Rounded up to the first moment the line's whole part reaches the target: 100000.01 ms becomes 100001 ms.
        val uneven = observation.copy(fullAt = fullAt + 1)
        assertEquals(now + 100 * second + 1, expected(rule(drones, VALUE, 101), uneven))
    }

    @Test fun endfieldSanityPlansBy432SecondsButFallsBackToTheFullTime() {
        val sanity = "endfield.sanity"
        val fullAt = now + 40 * 432 * second
        val observation = observe(sanity, 200, 240, fullAt = fullAt)
        assertEquals(fullAt, expected(rule(sanity, FULL), observation))
        assertEquals(fullAt - 24 * 432 * second, expected(rule(sanity, PERCENT, 90), observation))
        assertEquals(fullAt - 10 * 432 * second, expected(rule(sanity, VALUE, 230), observation))
        // Past the estimate without fresh data: the check is due as soon as the interval allows.
        val cached = observation.copy(fresh = false)
        assertEquals(fullAt - 24 * 432 * second, expected(rule(sanity, PERCENT, 90), cached, at = fullAt - hour))
        // Fresh data has overtaken the estimate (the real rate is slower): plan by the full time, which bounds it.
        val behind = observe(sanity, 210, 240, fetchedAt = fullAt - 12 * 432 * second, fullAt = fullAt)
        assertEquals(fullAt, expected(rule(sanity, PERCENT, 90), behind, at = fullAt - 12 * 432 * second))
    }

    @Test fun tradingAndManufactureOnlyGiveTheNextOutput() {
        for (metric in listOf("arknights.trading", "arknights.manufacture")) {
            val observation = observe(metric, 9, 20, nextAt = now + 3 * hour, fullAt = now + 9 * hour)
            for (rule in listOf(rule(metric, FULL), rule(metric, PERCENT, 80), rule(metric, VALUE, 15))) {
                assertEquals("$rule", now + 3 * hour, expected(rule, observation))
            }
            assertNull(expected(rule(metric, VALUE, 9), observation))
            assertNull(expected(rule(metric, FULL), observation.copy(nextAt = now)))
            assertNull(expected(rule(metric, FULL), observation.copy(nextAt = null)))
        }
    }

    @Test fun metricsWithoutTimesAreNotPlanned() {
        for (metric in listOf("arknights.dormitory_rest", "endfield.daily_activation", "endfield.weekly_mission",
            "endfield.pass_level")) {
            val observation = observe(metric, 3, 10, fullAt = now + hour, nextAt = now + hour)
            for (rule in listOf(rule(metric, FULL), rule(metric, PERCENT, 50), rule(metric, VALUE, 5))) {
                assertNull("$rule", expected(rule, observation))
            }
        }
        val event = rule("arknights.training", EVENT)
        assertEquals(now + hour, expected(event, training(EventState.RUNNING, fullAt = now + hour)))
        assertNull(expected(event, training(EventState.IDLE, round = null)))
        assertNull(expected(event, training(EventState.COMPLETE)))
        assertNull(expected(event, training(EventState.UNKNOWN, fullAt = now + hour)))
        assertEquals(now + hour, expected(rule("arknights.recruit", FULL), observe("arknights.recruit", 2, 4, fullAt = now + hour)))
    }

    @Test fun aRoleKeepsTheMinimumIntervalAndTheCalibrationPeriod() {
        val interval = ReminderSchedule.MIN_REQUEST_INTERVAL_MILLIS
        val period = ReminderSchedule.CALIBRATION_PERIOD_MILLIS
        assertEquals(30 * minute, interval)
        assertEquals(3 * hour, period)
        // The earliest expected time wins while it is before the calibration.
        assertEquals(now + 40 * minute, ReminderSchedule.nextCheck(listOf(now + 2 * hour, null, now + 40 * minute),
            now - hour, now - hour, now))
        // Never sooner than 30 minutes after the last request.
        assertEquals(now + 25 * minute, ReminderSchedule.nextCheck(listOf(now + 10 * minute), now - 5 * minute,
            now - 5 * minute, now))
        assertEquals(now + 25 * minute, ReminderSchedule.nextCheck(listOf(now - hour), now - 5 * minute, now - 5 * minute, now))
        // Calibration three hours after the last one, before a later expected time.
        assertEquals(now + hour, ReminderSchedule.nextCheck(listOf(now + 2 * hour), now - 2 * hour, now - 2 * hour, now))
        // Every rule met (or without a time of its own): only the calibration is left.
        assertEquals(now + 2 * hour, ReminderSchedule.nextCheck(listOf(null, null), now - hour, now - hour, now))
        // Never calibrated, or overdue: now, unless a request was made less than 30 minutes ago.
        assertEquals(now, ReminderSchedule.nextCheck(listOf(null), null, null, now))
        assertEquals(now, ReminderSchedule.nextCheck(listOf(null), now - 5 * hour, now - 5 * hour, now))
        assertEquals(now + 20 * minute, ReminderSchedule.nextCheck(listOf(null), null, now - 10 * minute, now))
        // A role without enabled rules is not scheduled.
        assertNull(ReminderSchedule.nextCheck(emptyList(), now - hour, now - hour, now))
    }

    @Test fun freshDataMovesTheRolesNextCheck() {
        val sanity = "arknights.sanity"
        val value = rule(sanity, VALUE, 120)
        val full = rule(sanity, FULL)
        val observation = observe(sanity, 100, 135, fullAt = now + 35 * 360 * second)
        val planned = listOf(value, full).map { judge(it, observation).expectedAt }
        assertEquals(listOf(now + 20 * 360 * second, now + 35 * 360 * second), planned)
        assertEquals(now + 20 * 360 * second, ReminderSchedule.nextCheck(planned, now, now, now))
        // Spent down to 10: both expected times move later and the calibration takes over.
        val spent = observe(sanity, 10, 135, fetchedAt = now + hour, fullAt = now + hour + 125 * 360 * second)
        val replanned = listOf(value, full).map { judge(it, spent, at = now + hour).expectedAt }
        assertEquals(listOf(now + hour + 110 * 360 * second, now + hour + 125 * 360 * second), replanned)
        assertEquals(now + 4 * hour, ReminderSchedule.nextCheck(replanned, now + hour, now + hour, now + hour))
    }
}
