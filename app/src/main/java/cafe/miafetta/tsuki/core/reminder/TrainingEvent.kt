package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.TrainingState

// How the training room reads as an event, kept here so that every caller judges it the same way.
object TrainingEvent {
    // A specialisation cannot be stopped or paused once it starts, so the parser's PAUSED (speed 0 with time left) has
    // no basis in the game and is judged as unknown, like UNKNOWN itself.
    fun state(state: TrainingState) = when (state) {
        TrainingState.IDLE -> EventState.IDLE
        TrainingState.TRAINING -> EventState.RUNNING
        TrainingState.COMPLETE -> EventState.COMPLETE
        TrainingState.UNKNOWN, TrainingState.PAUSED -> EventState.UNKNOWN
    }

    // A round is the trainee together with the target skill. The completion time is no identity: once a round is
    // complete it changes with every read. The format is stored in trigger records and must stay stable.
    fun round(traineeId: String?, skill: Int?): String? = if (traineeId == null || skill == null) null else "$traineeId/$skill"
}
