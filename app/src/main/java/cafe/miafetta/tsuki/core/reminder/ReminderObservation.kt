package cafe.miafetta.tsuki.core.reminder

// One metric of one role as the judge sees it. Times are epoch milliseconds.
//
// [value] and [limit] hold at [fetchedAt], the local time the data was obtained. [fullAt] and [nextAt] are on the same
// local clock: the readers map server timestamps onto receivedAt, which is also why a recovery line starts at
// fetchedAt. [dataAt] is the source's own data time (it may be the server's clock); it only orders observations of the
// same role and is never compared with local times.
//
// [limit] is the effective limit: null when unknown, 0 when the source reports "0/0". [value] may exceed it (Arknights
// sanity above its limit from items). [estimated] says the value itself is worked out rather than reported (trading,
// manufacture). [fullAt] is a reliable full or all-complete time (for an event, the completion time of the running
// round); a time at or before fetchedAt means it was already full. [nextAt] is the next output, which is not the time
// of being full.
//
// [fresh] is true only when the data was obtained just now. A failed request passes the last known observation with
// fresh = false; it is never reported as fresh, so that a failure cannot pass for a successful refresh.
//
// Event metrics also carry [round], which tells rounds apart, and [event], the round's state.
data class ReminderObservation(
    val subject: ReminderSubject,
    val metricId: String,
    val value: Int,
    val fetchedAt: Long,
    val fresh: Boolean,
    val limit: Int? = null,
    val state: ObservationState = ObservationState.VALID,
    val estimated: Boolean = false,
    val fullAt: Long? = null,
    val nextAt: Long? = null,
    val dataAt: Long = fetchedAt,
    val round: String? = null,
    val event: EventState? = null,
) {
    init {
        require(metricId.isNotBlank() && metricId.length <= 80)
        require(value >= 0 && (limit == null || limit >= 0))
        require(fetchedAt >= 0 && dataAt >= 0 && (fullAt == null || fullAt >= 0) && (nextAt == null || nextAt >= 0))
        require(round == null || round.isNotBlank() && round.length <= 120)
    }
}
