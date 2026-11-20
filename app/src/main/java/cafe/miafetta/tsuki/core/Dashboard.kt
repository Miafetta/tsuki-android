package cafe.miafetta.tsuki.core

internal val realtimeLabels = listOf("理智", "训练室", "公开招募", "公招刷新", "每周报酬合成玉",
    "每日任务", "每周任务", "数据增补仪", "数据增补条")
internal val buildingLabels = listOf("无人机", "休息进度", "订单进度", "制造进度", "干员疲劳", "线索收集")

internal fun dashboardMetric(snapshot: WidgetSnapshot?, label: String): WidgetMetric? {
    val key = when (label) {
        "每周报酬合成玉" -> "合成玉"
        "休息进度" -> "宿舍休息"
        else -> label
    }
    return snapshot?.let { it.metrics + it.dashboardMetrics }?.firstOrNull { canonicalMetricLabel(it.label) == key }
}

internal fun validateAccountUpdate(oldRoles: List<String>, newRoles: List<String>, otherRoles: List<String>) {
    require(newRoles.isNotEmpty() && oldRoles.any { it in newRoles }) { "Credential belongs to a different account" }
    require(newRoles.none { it in otherRoles }) { "Account already added" }
}

internal fun dashboardDetail(snapshot: WidgetSnapshot?, label: String, now: Long): String? {
    if (snapshot == null) return null
    if (label == "公招刷新") return dashboardMetric(snapshot, label)?.let {
        if (it.value > 0) "可进行公开招募标签刷新" else it.recoveryAt?.let { at -> "${durationWords(at - now)}后可刷新" }
    }
    // PAUSED, read back from snapshots of earlier versions, has no end time and falls through to the unknown wording.
    if (label == "训练室") return snapshot.training.let {
        when {
            it.state == TrainingState.COMPLETE || it.completedByTime(now) -> "干员已完成专精"
            it.endsAt != null -> "${durationWords(it.endsAt - now)}后完成专精"
            it.state == TrainingState.IDLE -> "暂无专精训练"
            else -> "专精时间未知"
        }
    }
    if (label in listOf("每日任务", "每周任务", "每周报酬合成玉", "数据增补仪", "数据增补条")) {
        if (dashboardMetric(snapshot, label) == null) return null
        val serverNow = snapshot.dataAt + now - snapshot.fetchedAt
        return "${durationWords(nextDashboardReset(label, serverNow) - serverNow)}后刷新"
    }
    val metric = dashboardMetric(snapshot, label) ?: return null
    if (label == "公开招募" && metric.maximum != null && metric.maximum > 0 && metric.value >= metric.maximum)
        return "招募已全部完成"
    val at = metric.recoveryAt ?: return null
    if (at <= now) return when (label) {
        "理智" -> "理智已全部恢复"
        "公开招募" -> "招募已全部完成"
        else -> "已完成"
    }
    val suffix = when (label) {
        "理智", "无人机" -> "全部恢复"
        "公开招募" -> "全部完成"
        else -> "完成"
    }
    return "${durationWords(at - now)}后$suffix"
}

internal fun dashboardNeedsRefresh(snapshot: WidgetSnapshot?, now: Long): Boolean =
    snapshot == null || now - snapshot.fetchedAt >= 5 * 60_000L

internal fun nextDashboardReset(label: String, at: Long): Long {
    val now = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.of("Asia/Shanghai"))
    val reset = if (label == "数据增补仪" || label == "数据增补条") {
        // The official status page uses the monthly 16th boundary for tower rewards.
        val boundary = now.withDayOfMonth(16).toLocalDate().atStartOfDay(now.zone)
        if (boundary.isAfter(now)) boundary else boundary.plusMonths(1)
    } else {
        var boundary = now.toLocalDate().atTime(4, 0).atZone(now.zone)
        if (!boundary.isAfter(now)) boundary = boundary.plusDays(1)
        if (label != "每日任务") while (boundary.dayOfWeek != java.time.DayOfWeek.MONDAY) boundary = boundary.plusDays(1)
        boundary
    }
    return reset.toInstant().toEpochMilli()
}

internal fun durationWords(millis: Long): String {
    val minutes = (millis.coerceAtLeast(0) + 59999) / 60000
    return if (minutes >= 1440) "${minutes / 1440}天${minutes % 1440 / 60}小时"
    else "${minutes / 60}小时${minutes % 60}分钟"
}

internal fun recoveryClock(at: Long?, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    if (at == null || at <= 0) return "未知"
    val target = java.time.Instant.ofEpochMilli(at).atZone(zone)
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = when (target.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> target.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))
    }
    return "$day ${target.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
}

internal fun syncAge(at: Long?, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    if (at == null || at <= 0) return "时间未知"
    val age = (now - at).coerceAtLeast(0)
    return when {
        age < 60_000L -> "刚刚"
        age < 3_600_000L -> "${age / 60_000} 分钟前"
        else -> java.time.Instant.ofEpochMilli(at).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
}
