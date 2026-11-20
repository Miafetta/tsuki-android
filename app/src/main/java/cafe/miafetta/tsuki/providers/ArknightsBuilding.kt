package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.MetricTimer
import cafe.miafetta.tsuki.core.TrainingState
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetTraining
import kotlin.math.ceil
import kotlin.math.floor

// Building metrics of ArknightsReader. Each throws on a missing or out-of-range field; the reader turns that into a note.

internal fun ArknightsReader.trading(): WidgetMetric {
    val tradings = building.getJSONArray("tradings")
    require(tradings.length() <= 10)
    var stock = 0
    var capacity = 0
    var estimated = false
    val times = mutableListOf<Long>()
    for (index in 0 until tradings.length()) {
        val item = tradings.getJSONObject(index)
        val limit = item.getInt("stockLimit").also { require(it in 0..100) }
        val stored = item.getJSONArray("stock").length().also { require(it <= limit) }
        val finish = item.getLong("completeWorkTime").also { require(it in -1..32_503_680_000L) }
        val workers = item.optJSONArray("chars")
        val working = workers == null || workers.length() > 0
        val updatedAt = item.optLong("lastUpdateTime", -1)
        // Match the official page's three-hour estimate only when staffed production is known.
        val produced = if (working && finish > 0 && finish <= serverNow) {
            if (workers != null && updatedAt in 0..serverNow) (serverNow - finish) / 10800 + 1 else 1L
        } else 0L
        val current = (stored.toLong() + produced).coerceAtMost(limit.toLong()).toInt()
        stock += current
        if (current != stored) estimated = true
        capacity += limit
        if (current < limit && working && finish > 0) {
            if (finish > serverNow) times.add(finish)
            else if (workers != null && updatedAt in 0..serverNow) times.add(finish + produced * 10800)
        }
    }
    return WidgetMetric("订单进度", stock, capacity.takeIf { it > 0 }, nextTime(times), MetricTimer.NEXT_COMPLETION,
        text = if (capacity == 0) "$stock/0" else null, estimated = estimated)
}

internal fun ArknightsReader.manufacture(): WidgetMetric {
    val manufactures = building.getJSONArray("manufactures")
    require(manufactures.length() <= 10)
    var complete = 0
    var capacity = 0
    var capacityKnown = true
    var estimated = false
    val times = mutableListOf<Long>()
    for (index in 0 until manufactures.length()) {
        val item = manufactures.getJSONObject(index)
        val stored = item.getInt("complete").also { require(it in 0..10000) }
        val formulaId = item.getString("formulaId")
        if (formulaId.isEmpty()) {
            complete += stored
            if (stored > 0) capacityKnown = false
            continue
        }
        val formula = data.getJSONObject("manufactureFormulaInfoMap").getJSONObject(formulaId)
        val weight = formula.getInt("weight").also { require(it in 1..10000) }
        val cost = formula.getInt("costPoint").also { require(it in 1..31_536_000) }
        val volume = item.getInt("capacity").also { require(it in 0..10000) }
        val limit = volume / weight
        require(stored <= limit)
        val used = item.getInt("weight").also { require(it in 0..volume) }
        val remaining = item.getInt("remain").also { require(it in 0..100000) }
        val target = minOf(limit, stored + minOf(remaining, (volume - used) / weight))
        val updatedAt = item.getLong("lastUpdateTime").also { require(it in 0..32_503_680_000L) }
        val finish = item.getLong("completeWorkTime").also { require(it in -1..32_503_680_000L) }
        val speed = item.getDouble("speed").also { require(it.isFinite() && it in 0.0..1000.0) }
        var current = stored
        if (finish > 0 && speed > 0 && stored < target) {
            val cycle = (cost / speed).also { require(it.isFinite()) }
            val produced = floor((serverNow - updatedAt).coerceAtLeast(0) / cycle)
            current = if (finish <= serverNow) target else stored + produced.coerceAtMost((target - stored).toDouble()).toInt()
            if (current < target) times.add(updatedAt + ceil((produced + 1) * cycle).coerceAtMost((finish - updatedAt).toDouble()).toLong())
        }
        complete += current
        if (current != stored) estimated = true
        capacity += limit
    }
    if (!capacityKnown) notes.add("制造进度 容量未知：已有成品但未提供配方")
    val text = when {
        !capacityKnown -> "${if (estimated) "≈" else ""}$complete/未知"
        capacity == 0 -> "$complete/0"
        else -> null
    }
    return WidgetMetric("制造进度", complete, capacity.takeIf { it > 0 && capacityKnown }, nextTime(times),
        MetricTimer.NEXT_COMPLETION, text = text, estimated = estimated)
}

internal fun ArknightsReader.recruit(): WidgetMetric {
    val recruit = data.getJSONArray("recruit")
    require(recruit.length() <= 4)
    var complete = 0
    var estimated = false
    val times = mutableListOf<Long>()
    for (index in 0 until recruit.length()) {
        val slot = recruit.getJSONObject(index)
        val state = slot.getInt("state").also { require(it in 1..3) }
        val finish = slot.getLong("finishTs")
        if (state == 3 || state == 2 && finish > 0 && finish <= serverNow) complete++
        if (state == 2 && finish > 0 && finish <= serverNow) estimated = true
        if (state == 2 && finish > serverNow && finish <= 32_503_680_000L) times.add(finish)
    }
    return WidgetMetric("公开招募", complete, recruit.length().takeIf { it > 0 }, times.maxOrNull()?.let(::localTime),
        MetricTimer.NEXT_COMPLETION, text = if (recruit.length() == 0) "0/0" else null, estimated = estimated)
}

internal fun ArknightsReader.trainingRoom(): WidgetMetric {
    val room = building.getJSONObject("training")
    require(room.has("trainee"))
    val trainee = room.optJSONObject("trainee")
    require(trainee != null || room.isNull("trainee"))
    if (trainee != null) {
        val id = trainee.getString("charId")
        training = WidgetTraining(traineeId = id, name = characterName(id), avatarUrl = OfficialAvatars.character(id))
    }
    val skill = trainee?.getInt("targetSkill")?.also { require(it in -1..2) } ?: -1
    if (skill == -1) {
        training = training.copy(state = TrainingState.IDLE)
        return WidgetMetric("训练室", 0, text = "空闲中")
    }
    val id = requireNotNull(trainee).getString("charId")
    val name = characterName(id)
    training = training.copy(skill = skill)
    val seconds = room.getLong("remainSecs").also { require(it in -1..31_536_000) }
    room.getLong("lastUpdateTime").also { require(it in 0..32_503_680_000L) }
    val speed = room.getDouble("speed").also { require(it.isFinite() && it in 0.0..1000.0) }
    val finish = serverNow + seconds
    require(seconds >= 0)
    // A specialisation cannot be stopped or paused once it starts, and it runs without an assistant (PRTS training room
    // entry), so speed 0 with time left has no known meaning and no end time to trust: it reads as unknown. PAUSED is
    // never produced; it stays in TrainingState for snapshots stored by earlier versions.
    val unknown = speed == 0.0 && seconds > 0
    // remainSecs is already remaining at currentTs; do not subtract lastUpdateTime elapsed twice.
    val state = when {
        unknown -> TrainingState.UNKNOWN
        seconds == 0L -> TrainingState.COMPLETE
        else -> TrainingState.TRAINING
    }
    val endsAt = if (unknown) null else localTime(finish)
    training = WidgetTraining(state, id, name, skill, endsAt, OfficialAvatars.character(id))
    return WidgetMetric("训练室", if (state == TrainingState.COMPLETE) 1 else 0, 1, endsAt,
        MetricTimer.NEXT_COMPLETION, text = "${name ?: "名称未知"}，${skill + 1}技能，${training.status(receivedAt)}")
}

private fun ArknightsReader.characterName(id: String) =
    data.optJSONObject("charInfoMap")?.optJSONObject(id)?.optString("name")?.takeIf { it.isNotBlank() }?.take(40)

internal fun ArknightsReader.dormitories(): WidgetMetric {
    val dormitories = building.getJSONArray("dormitories")
    require(dormitories.length() <= 4)
    var rested = 0
    var total = 0
    var estimated = false
    for (index in 0 until dormitories.length()) {
        val dormitory = dormitories.getJSONObject(index)
        val occupants = dormitory.getJSONArray("chars")
        val level = dormitory.optInt("level", -1)
        val comfort = dormitory.optInt("comfort", -1)
        val speed = if (level in 1..5 && comfort in 0..5000) 1.5 + .1 * level + .0004 * comfort else null
        require(occupants.length() <= 5)
        total += occupants.length()
        for (occupant in 0 until occupants.length()) {
            val character = occupants.getJSONObject(occupant)
            val mood = character.getInt("ap").also { require(it in 0..8640000) }
            val updatedAt = character.optLong("lastApAddTime", -1)
            val recovered = speed != null && updatedAt in 1..serverNow && mood + (serverNow - updatedAt) * speed * 100 >= 8640000
            if (mood == 8640000 || recovered) rested++
            if (mood < 8640000 && recovered) estimated = true
        }
    }
    return WidgetMetric("休息", rested, total.takeIf { it > 0 }, text = if (total == 0) "0/0" else null, estimated = estimated)
}
