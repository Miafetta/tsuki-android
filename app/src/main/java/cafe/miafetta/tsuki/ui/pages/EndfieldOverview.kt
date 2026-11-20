package cafe.miafetta.tsuki.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.dashboardDetail
import cafe.miafetta.tsuki.core.dashboardMetric
import cafe.miafetta.tsuki.core.recoveryClock
import cafe.miafetta.tsuki.ui.components.MetricCard
import cafe.miafetta.tsuki.ui.components.MetricRow
import cafe.miafetta.tsuki.ui.components.PageListSpacing
import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import top.yukonga.miuix.kmp.basic.Card

// The official Endfield realtime-data page: sanity first, then the other modules in the order set in Skland, which
// the parser already applied to the snapshot (EndfieldParser).
private val defaultRows = listOf("活跃度", "每周事务", "通行证", "蚀像寻遗")

internal fun LazyListScope.endfieldOverview(snapshot: WidgetSnapshot?, now: Long) {
    val sanity = dashboardMetric(snapshot, "理智")
    fadeItem("endfield-realtime") { Column {
        TsukiSmallTitle("实时数据", Modifier.testTag("section-title:realtime"), spacingAbove = PageListSpacing)
        // Skland draws Endfield sanity with the same brain icon as Arknights, so the card keeps its default icon.
        MetricCard("理智", sanity, dashboardDetail(snapshot, "理智", now) ?: "未知", recoveryClock(sanity?.recoveryAt, now))
    } }
    val rows = snapshot?.metrics?.drop(1)?.map { it.label }?.takeIf { it.isNotEmpty() } ?: defaultRows
    fadeItem("endfield-realtime-rows") { Card(Modifier.fillMaxWidth()) {
        rows.forEach { label -> MetricRow(label, dashboardMetric(snapshot, label), null) }
    } }
}
