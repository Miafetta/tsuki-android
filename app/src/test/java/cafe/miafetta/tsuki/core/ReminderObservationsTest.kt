package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.*
import org.junit.Test

// How snapshots read as reminder observations: the two sanity metrics, how a full or unknown recovery time reads, which
// snapshots identify their metrics by label, and that every metric offering reminders has a reader. The other metrics
// are covered by ReminderObservationSamplesTest (redacted samples) and ReminderObservationCasesTest (constructed).
class ReminderObservationsTest {
    private val now = 1_793_956_830_000L
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")

    private fun snapshot(vararg metrics: WidgetMetric) = WidgetSnapshot("角色", metrics.toList(), now, dataAt = now - 5_000)

    @Test fun arknightsSanityReadsValueLimitAndFullTime() {
        val snapshot = snapshot(WidgetMetric("理智", 120, 135, now + 5_400_000, estimated = true, metricId = "arknights.sanity"))
        val observation = ReminderObservations.from(arknights, "arknights.sanity", snapshot, fresh = true, arknightsSource = true)!!
        assertEquals(arknights, observation.subject)
        assertEquals("arknights.sanity", observation.metricId)
        assertEquals(120, observation.value)
        assertEquals(135, observation.limit)
        assertEquals(now + 5_400_000, observation.fullAt)
        assertEquals(now, observation.fetchedAt)
        assertEquals(now - 5_000, observation.dataAt)
        assertTrue(observation.fresh)
        assertTrue(observation.estimated)
        assertNull(observation.nextAt)
        // The cache read after a failed request is never fresh.
        assertFalse(ReminderObservations.from(arknights, "arknights.sanity", snapshot, fresh = false, arknightsSource = true)!!.fresh)
    }

    @Test fun aZeroRecoveryTimeIsFullAndAMissingOneIsUnknown() {
        val full = ReminderObservations.from(endfield, "endfield.sanity",
            snapshot(WidgetMetric("理智", 240, 240, 0, metricId = "endfield.sanity")), fresh = true, arknightsSource = false)!!
        // A full time at fetchedAt is how an observation says it was already full.
        assertEquals(now, full.fullAt)
        assertEquals(240, full.limit)
        val unknown = ReminderObservations.from(endfield, "endfield.sanity",
            snapshot(WidgetMetric("理智", 100, 240, null, metricId = "endfield.sanity")), fresh = true, arknightsSource = false)!!
        assertNull(unknown.fullAt)
        assertEquals(100, unknown.value)
        assertFalse(unknown.estimated)
        val noLimit = ReminderObservations.from(arknights, "arknights.sanity",
            snapshot(WidgetMetric("理智", 80)), fresh = true, arknightsSource = true)!!
        assertNull(noLimit.limit)
    }

    @Test fun arknightsSnapshotsWithoutIdsAreReadByLabelAndEndfieldSnapshotsNever() {
        // A snapshot as Arknights stored it before metric IDs existed.
        val legacy = snapshot(WidgetMetric("无人机", 50, 170, now), WidgetMetric("理智", 90, 135, now + 60_000))
        val read = ReminderObservations.from(arknights, "arknights.sanity", legacy, fresh = false, arknightsSource = true)!!
        assertEquals(90, read.value)
        assertEquals(now + 60_000, read.fullAt)
        // An Endfield widget record of 2.2.0 or 2.2.1: "理智" without an ID is no metric of either game.
        val unlabelled = snapshot(WidgetMetric("理智", 90, 240, now + 60_000))
        assertNull(ReminderObservations.from(endfield, "endfield.sanity", unlabelled, fresh = true, arknightsSource = false))
        assertNull(ReminderObservations.from(endfield, "arknights.sanity", unlabelled, fresh = true, arknightsSource = false))
        // Each game's sanity is its own metric.
        val endfieldSanity = snapshot(WidgetMetric("理智", 90, 240, now + 60_000, metricId = "endfield.sanity"))
        assertNull(ReminderObservations.from(endfield, "arknights.sanity", endfieldSanity, fresh = true, arknightsSource = false))
        assertNull(ReminderObservations.from(arknights, "endfield.sanity", legacy, fresh = true, arknightsSource = true))
    }

    @Test fun everyMetricOfferingRemindersHasAReader() {
        assertEquals(ReminderCapabilities.metricIds, ReminderObservations.metricIds)
        // A metric without a capability has no reader either.
        val snapshot = snapshot(WidgetMetric("理智", 90, 135, now), WidgetMetric("公招刷新", 2, 3, now + 60_000))
        assertNull(ReminderObservations.from(arknights, "arknights.recruit_refresh", snapshot, fresh = true, arknightsSource = true))
    }

    @Test fun aMetricTheSnapshotLacksReadsAsNoData() {
        val sanityOnly = snapshot(WidgetMetric("理智", 90, 135, now))
        for (metricId in ReminderCapabilities.metricIds.filter { it.startsWith("arknights.") && it != "arknights.sanity" }) {
            assertNull(metricId, ReminderObservations.from(arknights, metricId, sanityOnly, fresh = true, arknightsSource = true))
        }
        val activity = snapshot(WidgetMetric("活跃度", 50, 100, metricId = "endfield.daily_activation"))
        assertNull(ReminderObservations.from(endfield, "endfield.sanity", activity, fresh = true, arknightsSource = false))
        assertNull(ReminderObservations.from(endfield, "endfield.pass_level", activity, fresh = true, arknightsSource = false))
        // The training room reads its round from the snapshot's training, but only once the room itself was read.
        val untrained = sanityOnly.copy(training = WidgetTraining(TrainingState.TRAINING, "char_test_trainee", skill = 2,
            endsAt = now + 60_000))
        assertNull(ReminderObservations.from(arknights, "arknights.training", untrained, fresh = true, arknightsSource = true))
    }

    @Test fun endfieldMetricsAreNeverReadByLabel() {
        // Endfield widget records of 2.2.0 and 2.2.1 have no metric IDs until their next refresh.
        val legacy = snapshot(WidgetMetric("活跃度", 50, 100), WidgetMetric("每周事务", 3, 10), WidgetMetric("通行证", 9, 60))
        for (metricId in listOf("endfield.daily_activation", "endfield.weekly_mission", "endfield.pass_level")) {
            assertNull(metricId, ReminderObservations.from(endfield, metricId, legacy, fresh = true, arknightsSource = false))
        }
    }
}
