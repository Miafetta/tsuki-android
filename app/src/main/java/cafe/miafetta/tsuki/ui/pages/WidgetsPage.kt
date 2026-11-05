package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.EmptyCard
import cafe.miafetta.tsuki.ui.components.PreviewFadeMillis
import cafe.miafetta.tsuki.ui.components.PreviewTarget
import cafe.miafetta.tsuki.ui.components.WarmWidgetPreviews
import cafe.miafetta.tsuki.ui.components.WidgetPreviewStage
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.state.UiWidget
import cafe.miafetta.tsuki.ui.theme.widgetName
import cafe.miafetta.tsuki.ui.theme.widgetThemes

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.failureMessage
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight

// Switching between "已添加" and "全部小部件" replaces every card below the tabs. The cards cross-fade instead of cutting
// (C14), and while this page is the shown tab (warm) the other tab's previews are rendered ahead, so the first switch
// does not show empty stages. The pager composes this page next to Overview at launch; warming then would compete with
// the first data read.
internal fun LazyListScope.widgetPage(activity: Activity, widgets: List<UiWidget>, snapshot: WidgetSnapshot?,
    catalog: Int, previews: WidgetPreviews, catalogPreviewId: String, warm: Boolean,
    onCatalog: (Int) -> Unit, onConfigure: (UiWidget?, String) -> Unit) {
    val added = widgets.map { PreviewTarget(it.snapshot, it.theme == "DARK", addedPreviewId(it)) }
    val all = widgetThemes.map { (theme, _) ->
        theme to PreviewTarget(snapshot, theme == "DARK", "catalog:$catalogPreviewId:$theme")
    }
    item {
        TabRowWithContour(tabs = listOf("已添加 ${widgets.size}", "全部小部件"),
            selectedTabIndex = catalog, onTabSelected = onCatalog, cornerRadius = 17.5.dp,
            colors = TabRowDefaults.tabRowColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                selectedBackgroundColor = MiuixTheme.colorScheme.secondaryContainer))
        if (warm) WarmWidgetPreviews(previews, if (catalog == 0) all.map { it.second } else added)
    }

    if (catalog == 0 && widgets.isEmpty()) item(key = "empty") {
        Box(Modifier.animateItem(TabFade, null, TabFade)) { EmptyCard("还没有添加小部件", "浏览全部小部件") { onCatalog(1) } }
    }
    if (catalog == 0) widgets.zip(added) { widget, target -> item(key = widget.id) {
        WidgetCatalogItem(previews, target, { onConfigure(widget, widget.theme) },
            failure = widget.failure.takeIf { it.isNotEmpty() && it != RefreshFailure.RATE_LIMIT.name }
                ?.let { activity.getString(failureMessage(it)) }.orEmpty())
    } }
    else if (catalog == 1) all.forEach { (theme, target) -> item(key = "catalog:$theme") {
        WidgetCatalogItem(previews, target, { onConfigure(null, theme) })
    } }
}

private fun addedPreviewId(widget: UiWidget) = "widget:${widget.id}:${widget.accountId}:${widget.roleId}"

// Appearing and disappearing cards fade over the same time as a late preview; positions are not animated.
private val TabFade = tween<Float>(PreviewFadeMillis)

@Composable
private fun LazyItemScope.WidgetCatalogItem(previews: WidgetPreviews, target: PreviewTarget, onClick: () -> Unit,
    failure: String = "") {
    val (snapshot, dark, previewId) = target
    Card(modifier = Modifier.animateItem(TabFade, null, TabFade).fillMaxWidth().testTag("widget-preview:$previewId"),
        onClick = onClick) {
        WidgetPreviewStage(previews, snapshot, dark, previewId, imageModifier = Modifier.testTag("widget-image:$previewId"))
        // The preview already shows the role and data time; the row names the widget and reports failures.
        BasicComponent(title = widgetName(dark), summary = failure.ifEmpty { null },
            summaryColor = BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.error),
            endActions = { Icon(MiuixIcons.Basic.ArrowRight, null, Modifier.size(10.dp, 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions) })
    }
}
