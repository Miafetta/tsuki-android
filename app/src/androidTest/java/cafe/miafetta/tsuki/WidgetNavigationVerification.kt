package cafe.miafetta.tsuki

import cafe.miafetta.tsuki.ui.semanticsNodes
import cafe.miafetta.tsuki.ui.settleAccessibility
import androidx.compose.ui.semantics.getOrNull
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.net.toUri
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetRecord
import cafe.miafetta.tsuki.providers.AvatarCache
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.widget.StatusWidgetRenderer
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import cafe.miafetta.tsuki.widget.WidgetRuntime
import cafe.miafetta.tsuki.widget.widgetConfigureIntent
import cafe.miafetta.tsuki.widget.widgetConfigureUri
import cafe.miafetta.tsuki.widget.widgetOverviewIntent
import java.io.File

internal fun widgetNavigationVerification(runner: WidgetRenderInstrumentation) {
    val context = runner.targetContext
    val id = 2147483000
    fun findTab(text: String, node: AccessibilityNodeInfo? = runner.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) {
            var target: AccessibilityNodeInfo = node
            while (!target.isClickable) target = target.parent ?: break
            if (target.isClickable) return target
        }
        for (i in 0 until node.childCount) findTab(text, node.getChild(i))?.let { return it }
        return null
    }
    fun settle() = runner.settleAccessibility()
    fun expectNewOverview(previous: MainActivity) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (runner.uiActivity === previous && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
        check(runner.uiActivity !== previous) { "Widget reused the old navigation state" }
        settle()
        runner.runOnMainSync {
            val overview = checkNotNull(runner.uiActivity).semanticsNodes().single {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == "nav-tab:0"
            }
            check(overview.config[androidx.compose.ui.semantics.SemanticsProperties.Selected]) {
                "Widget click did not select Overview"
            }
        }
        check(!checkNotNull(runner.uiActivity).intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
    }
    val open = widgetOverviewIntent(context, id)
    check(open.component?.className == MainActivity::class.java.name && isWidgetOverviewIntent(open))
    check(open.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0 && open.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP == 0)
    check(!open.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) && widgetConfigId(open) == -1)
    val edit = Intent(Intent.ACTION_VIEW, "tsuki://configure/$id?miuiWidgetId=$id".toUri())
    check(widgetConfigId(edit) == id && edit.data.toString() == widgetConfigureUri(id))
    val configure = widgetConfigureIntent(context, id)
    check(configure.component?.className == WidgetConfigActivity::class.java.name && widgetConfigId(configure) == id)
    check(!configure.filterEquals(open)) { "Configure and Overview taps would share one PendingIntent" }
    check(!isWidgetOverviewIntent(Intent(context, MainActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("returnWidgetResult", true)))
    val renderer = StatusWidgetRenderer(AvatarCache(File(context.cacheDir, "navigation-test-avatars")))
    val config = WidgetInstanceConfig(id, "fake.skland", WidgetRuntime.FEATURE, "demo-1", "amiya")
    val record = WidgetRecord(config, FakeArknightsAdapter().fetch(config, System.currentTimeMillis()), RefreshState.READY)
    try {
        unconfiguredTapsOpenConfiguration(runner, renderer, id)
        // Configured cards, including one still waiting for its first data, open Overview.
        for (variant in listOf("native", "bitmap", "loading")) {
            settle()
            check(checkNotNull(findTab("我的")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            settle()
            val previous = checkNotNull(runner.uiActivity)
            runner.runOnMainSync {
                val views = when (variant) {
                    "native" -> renderer.renderAtSize(context, id, record, false, 360f, 162f, native = true)
                    "bitmap" -> renderer.renderWithSurface(context, id, record, false, false)
                    else -> renderer.renderNative(context, id, record.copy(snapshot = null, state = RefreshState.LOADING), false,
                        Bundle())
                }
                val root = views.apply(context, FrameLayout(context))
                check(root.findViewById<View>(android.R.id.background).performClick())
            }
            expectNewOverview(previous)
        }
        val previous = checkNotNull(runner.uiActivity)
        val legacy = Intent(context, MainActivity::class.java).setData("tsuki://configure/$id".toUri())
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runner.runOnMainSync { context.startActivity(legacy) }
        expectNewOverview(previous)
    } finally { runner.runOnMainSync { runner.uiActivity?.finish() } }
}

// An unconfigured card asks to be tapped for configuration: both renderers must open WidgetConfigActivity for it.
private fun unconfiguredTapsOpenConfiguration(runner: WidgetRenderInstrumentation, renderer: StatusWidgetRenderer, id: Int) {
    val context = runner.targetContext
    for (native in listOf(true, false)) {
        val monitor = runner.addMonitor(WidgetConfigActivity::class.java.name, null, false)
        try {
            runner.runOnMainSync {
                val views = if (native) renderer.renderAtSize(context, id, null, false, 360f, 162f, native = true)
                    else renderer.renderWithSurface(context, id, null, false, false)
                check(views.apply(context, FrameLayout(context)).findViewById<View>(android.R.id.background).performClick())
            }
            val editor = runner.waitForMonitorWithTimeout(monitor, 10_000) as? WidgetConfigActivity
            checkNotNull(editor) { "Unconfigured widget tap did not open configuration (native=$native)" }
            check(widgetConfigId(editor.intent) == id && editor.intent.data?.toString() == widgetConfigureUri(id))
            // The test instance is not placed, so the editor closes itself without reading or writing any widget.
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (!editor.isDestroyed && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
            check(editor.isDestroyed) { "Editor for an unplaced instance stayed open" }
        } finally { runner.removeMonitor(monitor) }
    }
}
