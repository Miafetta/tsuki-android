package cafe.miafetta.tsuki.core.reminder

// How a value recovers towards its limit, used to work back from the full-recovery time to the time a lower target is
// reached. Only for value < target <= limit and observedAt < fullAt; null when the model gives no time.
sealed interface RecoveryModel {
    fun reachAt(value: Int, limit: Int, target: Int, observedAt: Long, fullAt: Long): Long?

    // Only the full or completion time itself is known.
    data object None : RecoveryModel {
        override fun reachAt(value: Int, limit: Int, target: Int, observedAt: Long, fullAt: Long): Long? = null
    }

    // One point every [millisPerPoint] up to fullAt: the target is reached (limit - target) points before it.
    data class FixedRate(val millisPerPoint: Long) : RecoveryModel {
        init { require(millisPerPoint > 0) }

        override fun reachAt(value: Int, limit: Int, target: Int, observedAt: Long, fullAt: Long) =
            fullAt - (limit - target).toLong() * millisPerPoint
    }

    // A straight line from the value at observedAt to the limit at fullAt, rounded up to the first moment the line's
    // whole part reaches the target; the Arknights reader shows the current drone count the same way.
    data object Linear : RecoveryModel {
        override fun reachAt(value: Int, limit: Int, target: Int, observedAt: Long, fullAt: Long): Long? {
            if (target <= value || target > limit || fullAt <= observedAt) return null
            val gained = (target - value).toLong() * (fullAt - observedAt)
            val span = (limit - value).toLong()
            return observedAt + (gained + span - 1) / span
        }
    }
}
