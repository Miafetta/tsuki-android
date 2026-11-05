package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.TrainingPortrait
import cafe.miafetta.tsuki.ui.components.EmptyGuide
import cafe.miafetta.tsuki.ui.components.MetricCard
import cafe.miafetta.tsuki.ui.components.MetricRow
import cafe.miafetta.tsuki.ui.components.RoleCard
import cafe.miafetta.tsuki.ui.components.TrainingCard
import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.components.WidgetPreviewStage
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import cafe.miafetta.tsuki.core.*
import top.yukonga.miuix.kmp.basic.*

internal fun dataTime(snapshot: WidgetSnapshot?): String = syncAge(snapshot?.dataAt?.takeIf { snapshot.dataTimeKnown },
    System.currentTimeMillis())

internal fun LazyListScope.homePage(roles: List<UiRole>, selected: UiRole?, snapshot: WidgetSnapshot?,
    onSelect: (UiRole) -> Unit, onAccount: () -> Unit, failure: String = "",
    previews: WidgetPreviews? = null, portraits: (suspend (WidgetTraining, Boolean) -> ImageBitmap?)? = null) {
    val now = System.currentTimeMillis()
    if (selected == null) {
        item("empty-guide") { EmptyGuide(onAccount) }
        if (previews != null) item("empty-stage") { Card { WidgetPreviewStage(previews, null, isTsukiDarkTheme(), "empty") } }
        return
    }
    item("role") { RoleCard(roles, selected, failure.ifEmpty { dataTime(snapshot) }, failure.isNotEmpty(), onSelect) }
    item("realtime") { Column {
        TsukiSmallTitle("实时数据", Modifier.testTag("section-title:realtime"))
        MetricCard("理智", dashboardMetric(snapshot, "理智"), dashboardDetail(snapshot, "理智", now) ?: "未知",
            recoveryClock(dashboardMetric(snapshot, "理智")?.recoveryAt, now))
    } }
    item("training") {
        TrainingCard(snapshot?.training?.let { it.name ?: it.status(now) } ?: "未知", dashboardDetail(snapshot, "训练室", now) ?: "未知") {
            TrainingPortrait(snapshot?.training ?: WidgetTraining(), snapshot?.fetchedAt ?: 0, portraits, it)
        }
    }
    item("realtime-rows") { Card(Modifier.fillMaxWidth()) {
        realtimeLabels.drop(2).forEach { label -> MetricRow(label, dashboardMetric(snapshot, label), dashboardDetail(snapshot,
            label, now)) }
    } }
    item("building") { Column {
        TsukiSmallTitle("基建数据", Modifier.testTag("section-title:building"))
        MetricCard("无人机", dashboardMetric(snapshot, "无人机"), dashboardDetail(snapshot, "无人机", now) ?: "未知",
            recoveryClock(dashboardMetric(snapshot, "无人机")?.recoveryAt, now))
    } }
    item("building-rows") { Card(Modifier.fillMaxWidth()) {
        buildingLabels.drop(1).forEach { label -> MetricRow(label, dashboardMetric(snapshot, label), dashboardDetail(snapshot,
            label, now)) }
    } }
}
