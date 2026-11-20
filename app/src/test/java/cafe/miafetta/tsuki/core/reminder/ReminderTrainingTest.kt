package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.TrainingState
import cafe.miafetta.tsuki.core.reminder.EventState.COMPLETE
import cafe.miafetta.tsuki.core.reminder.EventState.IDLE
import cafe.miafetta.tsuki.core.reminder.EventState.RUNNING
import cafe.miafetta.tsuki.core.reminder.EventState.UNKNOWN
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOTIFIED
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOT_MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.UNAVAILABLE
import org.junit.Assert.*
import org.junit.Test

// The training room as an event: one report per round, re-armed by a fresh non-complete state or a new round.
class ReminderTrainingTest : ReminderFixture() {
    private val event = rule("arknights.training", ReminderMode.EVENT)

    @Test fun aRoundReportsOnceAndTheNextRoundAfterIdleReportsAgain() {
        val dueAt = now + 2 * hour
        val running = training(RUNNING, fullAt = dueAt)
        val decisions = event.walk(
            running to now,
            // Due without fresh data: the completion time comes from the API, so it may be worked out.
            running.copy(fresh = false) to dueAt,
            training(COMPLETE, fetchedAt = now + 3 * hour) to now + 3 * hour,
            training(IDLE, round = null, fetchedAt = now + 4 * hour) to now + 4 * hour,
            training(RUNNING, fullAt = now + 8 * hour, round = ROUND_B, fetchedAt = now + 5 * hour) to now + 5 * hour,
            training(COMPLETE, round = ROUND_B, fetchedAt = now + 9 * hour) to now + 9 * hour,
        )
        assertStatuses(listOf(NOT_MET, NOTIFIED, MET, NOT_MET, NOT_MET, NOTIFIED), decisions)
        assertEquals(dueAt, decisions[0].expectedAt)
        val notice = decisions[1].notice!!
        assertEquals("训练室专精训练已完成", notice.body)
        assertTrue(notice.estimated)
        assertEquals(ROUND_A, notice.round)
        assertEquals(ROUND_A, decisions[1].record.firedRound)
        assertFalse(decisions[3].record.fired)
        assertEquals(now + 8 * hour, decisions[4].expectedAt)
        assertEquals(ROUND_B, decisions[5].record.firedRound)
        assertFalse(decisions[5].notice!!.estimated)
    }

    @Test fun anotherRoundInTheCompleteStateReportsAgain() {
        // Collected and restarted with another trainee or skill between two reads.
        val decisions = event.walk(training(COMPLETE) to now, training(COMPLETE, round = ROUND_B) to now + hour,
            training(COMPLETE, round = ROUND_B) to now + 2 * hour, training(COMPLETE, round = null) to now + 3 * hour)
        assertStatuses(listOf(NOTIFIED, NOTIFIED, MET, MET), decisions)
        // A round worked out from the last known data is not new data.
        val fired = decisions[0].record
        assertEquals(MET, judge(event, training(COMPLETE, fresh = false, round = ROUND_B), fired, now + hour).status)
    }

    @Test fun unknownStatesNeitherReportNorReArm() {
        val fired = judge(event, training(COMPLETE)).record
        val unknown = listOf(training(UNKNOWN), training(UNKNOWN, fresh = false),
            observe("arknights.training", 0, null, round = ROUND_A),
            observe("arknights.training", 0, null, state = ObservationState.UNKNOWN, event = IDLE))
        for (observation in unknown) {
            assertEquals(observation.toString(), ReminderDecision(UNAVAILABLE, fired), judge(event, observation, fired, now + hour))
            assertEquals(UNAVAILABLE, judge(event, observation).status)
        }
        // Still the same round after the unknown read: no second report.
        val after = event.walk(training(UNKNOWN) to now + hour, training(COMPLETE) to now + 2 * hour, record = fired)
        assertStatuses(listOf(UNAVAILABLE, MET), after)
    }

    @Test fun pausedHasNoBasisAndReadsAsUnknown() {
        assertEquals(UNKNOWN, TrainingEvent.state(TrainingState.PAUSED))
        assertEquals(UNKNOWN, TrainingEvent.state(TrainingState.UNKNOWN))
        assertEquals(IDLE, TrainingEvent.state(TrainingState.IDLE))
        assertEquals(RUNNING, TrainingEvent.state(TrainingState.TRAINING))
        assertEquals(COMPLETE, TrainingEvent.state(TrainingState.COMPLETE))
        assertEquals("char_002_amiya/2", TrainingEvent.round("char_002_amiya", 2))
        assertNotEquals(TrainingEvent.round("char_002_amiya", 1), TrainingEvent.round("char_002_amiya", 2))
        assertNull(TrainingEvent.round(null, 2))
        assertNull(TrainingEvent.round("char_002_amiya", null))
    }

    @Test fun aMovedCompletionTimeIsFollowed() {
        // Changing the assistant changes the speed; the latest observation decides.
        val first = training(RUNNING, fullAt = now + 4 * hour)
        val later = training(RUNNING, fullAt = now + 6 * hour, fetchedAt = now + hour)
        val sooner = training(RUNNING, fullAt = now + 3 * hour, fetchedAt = now + 2 * hour)
        val decisions = event.walk(first to now, later to now + hour, later.copy(fresh = false) to now + 4 * hour,
            sooner to now + 2 * hour, sooner.copy(fresh = false) to now + 3 * hour)
        assertStatuses(listOf(NOT_MET, NOT_MET, NOT_MET, NOT_MET, NOTIFIED), decisions)
        assertEquals(listOf(now + 4 * hour, now + 6 * hour, now + 6 * hour, now + 3 * hour, null), decisions.map { it.expectedAt })
    }

    @Test fun aCompletionWorkedOutTooEarlyIsCorrectedByFreshData() {
        val running = training(RUNNING, fullAt = now + hour)
        // Reported at the known time, but fresh data says the round is still running: that is a non-complete state,
        // so the rule re-arms and reports the real completion.
        val decisions = event.walk(running to now, running.copy(fresh = false) to now + hour,
            training(RUNNING, fullAt = now + 3 * hour, fetchedAt = now + 2 * hour) to now + 2 * hour,
            training(COMPLETE, fetchedAt = now + 3 * hour) to now + 3 * hour)
        assertStatuses(listOf(NOT_MET, NOTIFIED, NOT_MET, NOTIFIED), decisions)
    }

    @Test fun enablingReportsACompleteRoundOnceAndReplaysNothing() {
        assertStatuses(listOf(NOTIFIED, MET), event.walk(training(COMPLETE) to now, training(COMPLETE) to now + hour))
        // The round that completed while the rule was off was collected already.
        assertStatuses(listOf(NOT_MET), event.walk(training(IDLE, round = null) to now))
    }
}
