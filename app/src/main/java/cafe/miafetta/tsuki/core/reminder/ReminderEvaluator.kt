package cafe.miafetta.tsuki.core.reminder

// Judges one rule against the latest observation of its metric. Pure: the caller keeps the returned record in place of
// the old one and sends the notice, if any. It takes no widget ID; a role's rule has one record however many widgets
// show the role.
//
// A rule reports once when it turns from unmet to met and stays quiet while it remains met. Only fresh data showing
// it unmet re-arms it; a worked-out result never does. A record kept for another mode or threshold (including the
// empty record of a rule just enabled) starts over, so a rule already met reports once on its first fresh data. An
// event reports once per round: after reporting, it needs a fresh non-complete state, or a fresh complete state of a
// different round, to report again.
object ReminderEvaluator {
    fun evaluate(rule: ReminderRule, capability: ReminderCapability?, observation: ReminderObservation?,
        record: TriggerRecord, now: Long): ReminderDecision {
        if (!rule.enabled) return ReminderDecision(ReminderStatus.DISABLED, TriggerRecord())
        if (capability == null || rule.mode !in capability.modes) return ReminderDecision(ReminderStatus.UNSUPPORTED, record)
        require(capability.metricId == rule.metricId)
        val kept = if (record.keptFor(rule)) record else TriggerRecord(rule.mode, rule.threshold)
        if (observation == null) return ReminderDecision(ReminderStatus.AWAITING_DATA, kept)
        require(observation.subject == rule.subject && observation.metricId == rule.metricId)
        val judgedAt = kept.dataAt
        // Data older than the fresh data already judged says nothing new, whether it arrives fresh or from a cache.
        if (judgedAt != null && observation.dataAt < judgedAt) {
            return ReminderDecision(ReminderStatus.STALE, kept)
        }
        ReminderCondition.blocker(rule, observation)?.let { return ReminderDecision(it, kept) }
        val expectedAt = ReminderSchedule.expectedAt(rule, capability, observation, now)
        // Without fresh data only the modes the capability allows may be judged from known times, and only after the
        // rule has judged fresh data: a cache from before the rule was enabled would replay what already happened
        // (a round completed and collected meanwhile), which a rule just enabled must not do.
        if (!observation.fresh && (rule.mode !in capability.projected || judgedAt == null)) {
            return ReminderDecision(ReminderStatus.AWAITING_DATA, kept, expectedAt = expectedAt)
        }
        val observed = ReminderCondition.observedMet(rule, observation)
        val met = observed || ReminderCondition.projectedMet(rule, capability, observation, now)
        val judged = if (observation.fresh) kept.copy(dataAt = observation.dataAt) else kept
        return when {
            met -> report(rule, capability, observation, judged, estimated = !(observation.fresh && observed), now)
            observation.fresh -> ReminderDecision(ReminderStatus.NOT_MET, judged.copy(fired = false), expectedAt = expectedAt)
            else -> ReminderDecision(ReminderStatus.NOT_MET, judged, expectedAt = expectedAt)
        }
    }

    private fun report(rule: ReminderRule, capability: ReminderCapability, observation: ReminderObservation,
        record: TriggerRecord, estimated: Boolean, now: Long): ReminderDecision {
        // Fresh data of another round in the complete state is a new round, even without a non-complete state between.
        val round = observation.round
        val newRound = rule.mode == ReminderMode.EVENT && observation.fresh && round != null &&
            record.firedRound != null && round != record.firedRound
        if (record.fired && !newRound) return ReminderDecision(ReminderStatus.MET, record)
        val body = requireNotNull(capability.texts.body(rule.mode, rule.threshold))
        val notice = ReminderNotice(rule.subject, rule.metricId, rule.mode, rule.threshold, body, estimated, round)
        val fired = record.copy(fired = true, firedAt = now, firedRound = round)
        return ReminderDecision(ReminderStatus.NOTIFIED, fired, notice)
    }
}
