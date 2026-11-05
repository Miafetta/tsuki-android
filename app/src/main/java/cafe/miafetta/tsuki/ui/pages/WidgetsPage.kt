package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.EmptyCard
import cafe.miafetta.tsuki.ui.components.WidgetPreviewStage
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.state.UiWidget
import cafe.miafetta.tsuki.ui.theme.widgetName
import cafe.miafetta.tsuki.ui.theme.widgetThemes

import android.app.Activity
import androidx.compose.foundation.layout.*
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

internal fun LazyListScope.widgetPage(activity: Activity, widgets: List<UiWidget>, snapshot: WidgetSnapshot?,
    catalog: Int, previews: WidgetPreviews, catalogPreviewId: String,
    onCatalog: (Int) -> Unit, onConfigure: (UiWidget?, String) -> Unit) {
    item {
        TabRowWithContour(tabs = listOf("已添加 ${widgets.size}", "全部小部件"),
            selectedTabIndex = catalog, onTabSelected = onCatalog, cornerRadius = 17.5.dp,
            colors = TabRowDefaults.tabRowColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                selectedBackgroundColor = MiuixTheme.colorScheme.secondaryContainer))
    }

    if (catalog == 0 && widgets.isEmpty()) item { EmptyCard("还没有添加小部件", "浏览全部小部件") { onCatalog(1) } }
    if (catalog == 0) widgets.forEach { widget -> item(key = widget.id) {
        WidgetCatalogItem(previews, widget.snapshot, widget.theme == "DARK",
            "widget:${widget.id}:${widget.accountId}:${widget.roleId}", { onConfigure(widget, widget.theme) },
            failure = widget.failure.takeIf { it.isNotEmpty() && it != RefreshFailure.RATE_LIMIT.name }
                ?.let { activity.getString(failureMessage(it)) }.orEmpty())
    } }
    else if (catalog == 1) widgetThemes.forEach { (theme, _) -> item(key = "catalog:$theme") {
        WidgetCatalogItem(previews, snapshot, theme == "DARK", "catalog:$catalogPreviewId:$theme", { onConfigure(null, theme) })
    } }
}

@Composable
private fun WidgetCatalogItem(previews: WidgetPreviews, snapshot: WidgetSnapshot?, dark: Boolean, previewId: String,
    onClick: () -> Unit, failure: String = "") {
    Card(modifier = Modifier.fillMaxWidth().testTag("widget-preview:$previewId"), onClick = onClick) {
        WidgetPreviewStage(previews, snapshot, dark, previewId, imageModifier = Modifier.testTag("widget-image:$previewId"))
        // The preview already shows the role and data time; the row names the widget and reports failures.
        BasicComponent(title = widgetName(dark), summary = failure.ifEmpty { null },
            summaryColor = BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.error),
            endActions = { Icon(MiuixIcons.Basic.ArrowRight, null, Modifier.size(10.dp, 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions) })
    }
}
