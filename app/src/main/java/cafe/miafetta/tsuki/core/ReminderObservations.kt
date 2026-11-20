package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.core.reminder.EventState
import cafe.miafetta.tsuki.core.reminder.ObservationState
import cafe.miafetta.tsuki.core.reminder.ReminderObservation
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.core.reminder.TrainingEvent

// How a role's snapshot reads as the observation of one reminder metric (core/reminder/ReminderObservation.kt). Every
// metric has a reader of its own, since what the snapshot's fields mean differs from metric to metric. A metric missing
// from the snapshot reads as null, which the evaluator takes as awaiting data. Every metric of ReminderCapabilities has
// a reader; ReminderObservationsTest keeps the two sets equal.
internal object ReminderObservations {
    private val readers: Map<String, (Source) -> ReminderObservation?> = mapOf(
        "arknights.sanity" to ::recovering,
        "arknights.drones" to ::recovering,
        "arknights.dormitory_rest" to ::counted,
        "arknights.training" to ::training,
        "arknights.recruit" to ::recruit,
        "arknights.trading" to ::output,
        "arknights.manufacture" to ::output,
        "endfield.sanity" to ::recovering,
        "endfield.daily_activation" to ::counted,
        "endfield.weekly_mission" to ::counted,
        "endfield.pass_level" to ::counted,
    )

    val metricIds: Set<String> get() = readers.keys

    // [fresh] is true only for a snapshot obtained just now; the role cache read after a failed request is not fresh.
    // [arknightsSource] says whether the snapshot comes from an Arknights data source, whose snapshots stored before
    // metric IDs existed are identified by label (MetricIds.kt); the caller knows the data source, core does not.
    fun from(subject: ReminderSubject, metricId: String, snapshot: WidgetSnapshot, fresh: Boolean,
        arknightsSource: Boolean): ReminderObservation? =
        readers[metricId]?.invoke(Source(subject, metricId, snapshot, fresh, arknightsSource))

    private class Source(val subject: ReminderSubject, val metricId: String, val snapshot: WidgetSnapshot, val fresh: Boolean,
        private val arknightsSource: Boolean) {
        val metric get() = snapshot.metrics.firstOrNull { it.stableId(arknightsSource) == metricId }

        // Times of the data always come from the snapshot itself.
        fun observation(value: Int, limit: Int?, state: ObservationState = ObservationState.VALID, estimated: Boolean = false,
            fullAt: Long? = null, nextAt: Long? = null, round: String? = null, event: EventState? = null) =
            ReminderObservation(subject, metricId, value, snapshot.fetchedAt, fresh, limit, state, estimated, fullAt, nextAt,
                snapshot.dataAt, round, event)
    }

    // The effective limit: the reported maximum, 0 where the source reports nothing to count ("0/0", "x/0"), and null
    // when it is unknown ("x/未知"). The parsers leave maximum null in all three cases; only the text tells them apart.
    private fun WidgetMetric.limit(): Int? = maximum ?: if (text?.endsWith("/0") == true) 0 else null

    private fun ReminderObservation.emptyWhenZero() = if (limit == 0) copy(state = ObservationState.NO_OBJECT) else this

    // A value recovering towards its maximum. The readers set recoveryAt to the local full time, 0 once full and null
    // when the time is unknown; a full time at fetchedAt is how an observation says it was already full. Arknights
    // sanity and drones between syncs are worked out from their full time, which the metric marks as estimated.
    private fun recovering(source: Source): ReminderObservation? {
        val metric = source.metric ?: return null
        val fullAt = when (val recoveryAt = metric.recoveryAt) {
            null -> null
            0L -> source.snapshot.fetchedAt
            else -> recoveryAt
        }
        return source.observation(metric.value, metric.maximum, estimated = metric.estimated, fullAt = fullAt)
    }

    // A count against its maximum without a time: resting operators who have fully rested, and the Endfield progress
    // modules. "0/0" (no operator resting) is no object to count.
    private fun counted(source: Source): ReminderObservation? {
        val metric = source.metric ?: return null
        return source.observation(metric.value, metric.limit(), estimated = metric.estimated).emptyWhenZero()
    }

    // Trading and manufacture: the stock (an estimate between syncs) against the capacity, and the next output time,
    // which is never the time of being full. Manufacture with an unknown capacity keeps a valid value without a limit,
    // so only value rules can be judged.
    private fun output(source: Source): ReminderObservation? {
        val metric = source.metric ?: return null
        return source.observation(metric.value, metric.limit(), estimated = metric.estimated, nextAt = metric.recoveryAt)
            .emptyWhenZero()
    }

    // Recruitment is full once every slot in use is complete; idle slots do not count. The metric counts complete slots
    // against all slots and keeps the latest finish time of the running slots (null when none runs), so the limit here
    // is built for the full rule alone and is no slot count: the complete slots when none runs (full), one more than
    // that while one runs (not full, full at that finish time), and 0 when no slot is in use or there are no slots.
    private fun recruit(source: Source): ReminderObservation? {
        val metric = source.metric ?: return null
        val lastFinish = metric.recoveryAt
        val limit = if (lastFinish != null) metric.value + 1 else metric.value
        val fullAt = lastFinish ?: source.snapshot.fetchedAt.takeIf { limit > 0 }
        return source.observation(metric.value, limit, estimated = metric.estimated, fullAt = fullAt).emptyWhenZero()
    }

    // The training room is an event read from the snapshot's training; its metric only says the room was read. The
    // round is the trainee and target skill, and the end time is the completion time of a running round. An unknown
    // state (including PAUSED read back from snapshots of earlier versions) leaves the whole observation unknown.
    private fun training(source: Source): ReminderObservation? {
        source.metric ?: return null
        val training = source.snapshot.training
        val event = TrainingEvent.state(training.state)
        val state = if (event == EventState.UNKNOWN) ObservationState.UNKNOWN else ObservationState.VALID
        return source.observation(0, null, state, fullAt = training.endsAt,
            round = TrainingEvent.round(training.traineeId, training.skill), event = event)
    }
}
