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
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.ui.theme.isTsukiDarkTheme

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import cafe.miafetta.tsuki.ui.components.PreviewFadeMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import cafe.miafetta.tsuki.core.*
import top.yukonga.miuix.kmp.basic.*

internal fun dataTime(snapshot: WidgetSnapshot?): String = syncAge(snapshot?.dataAt?.takeIf { snapshot.dataTimeKnown },
    System.currentTimeMillis())

internal fun LazyListScope.homePage(roles: List<UiRole>, selected: UiRole?, snapshot: WidgetSnapshot?,
    onSelect: (UiRole) -> Unit, onAccount: () -> Unit, failure: String = "",
    previews: WidgetPreviews? = null, portraits: (suspend (WidgetTraining, Boolean) -> ImageBitmap?)? = null,
    game: String = Branding.GAME_ID, hasAccounts: Boolean = false) {
    val now = System.currentTimeMillis()
    if (selected == null) {
        // Accounts added before Endfield support list its roles only after the account is verified again.
        fadeItem("empty-guide:$game") {
            if (hasAccounts) EmptyGuide(onAccount, "账号中还没有${Branding.gameName(game)}角色",
                "已在${Branding.PLATFORM}绑定的角色，可在“我的”中更新 Cred（留空即可）后显示")
            else EmptyGuide(onAccount)
        }
        if (previews != null && game == Branding.GAME_ID) fadeItem("empty-stage") {
            Card { WidgetPreviewStage(previews, null, isTsukiDarkTheme(), "empty") }
        }
        return
    }
    fadeItem("role:${selected.game}") { RoleCard(roles, selected, failure.ifEmpty { dataTime(snapshot) }, failure.isNotEmpty(), onSelect) }
    if (selected.game == Branding.ENDFIELD_ID) {
        endfieldOverview(snapshot, now)
        return
    }
    fadeItem("realtime") { Column {
        TsukiSmallTitle("实时数据", Modifier.testTag("section-title:realtime"))
        MetricCard("理智", dashboardMetric(snapshot, "理智"), dashboardDetail(snapshot, "理智", now) ?: "未知",
            recoveryClock(dashboardMetric(snapshot, "理智")?.recoveryAt, now))
    } }
    fadeItem("training") {
        TrainingCard(snapshot?.training?.let { it.name ?: it.status(now) } ?: "未知", dashboardDetail(snapshot, "训练室", now) ?: "未知") {
            TrainingPortrait(snapshot?.training ?: WidgetTraining(), snapshot?.fetchedAt ?: 0, portraits, it)
        }
    }
    fadeItem("realtime-rows") { Card(Modifier.fillMaxWidth()) {
        realtimeLabels.drop(2).forEach { label -> MetricRow(label, dashboardMetric(snapshot, label), dashboardDetail(snapshot,
            label, now)) }
    } }
    fadeItem("building") { Column {
        TsukiSmallTitle("基建数据", Modifier.testTag("section-title:building"))
        MetricCard("无人机", dashboardMetric(snapshot, "无人机"), dashboardDetail(snapshot, "无人机", now) ?: "未知",
            recoveryClock(dashboardMetric(snapshot, "无人机")?.recoveryAt, now))
    } }
    fadeItem("building-rows") { Card(Modifier.fillMaxWidth()) {
        buildingLabels.drop(1).forEach { label -> MetricRow(label, dashboardMetric(snapshot, label), dashboardDetail(snapshot,
            label, now)) }
    } }
}

// A game tab replaces the overview's items. Each fades in and out over the Widgets page's cross-fade time (C14) instead of
// cutting, and the keys differ per game, so a switch never reuses an item of the other game.
private val GameFade = tween<Float>(PreviewFadeMillis)

internal fun LazyListScope.fadeItem(key: String, content: @Composable LazyItemScope.() -> Unit) =
    item(key) { Box(Modifier.animateItem(GameFade, null, GameFade)) { content() } }