package cafe.miafetta.tsuki

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import cafe.miafetta.tsuki.widget.StatusWidgetProvider
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation

internal fun widgetEditorVerification(runner: WidgetRenderInstrumentation) {
    val id = StatusWidgetProvider.owned(runner.targetContext).firstOrNull()
        ?: error("Requires an existing widget; this test never creates or changes one")
    val editorMonitor = runner.addMonitor(WidgetConfigActivity::class.java.name, null, false)
    val mainMonitor = runner.addMonitor(MainActivity::class.java.name, null, false)
    var editor: WidgetConfigActivity? = null
    fun findClose(node: AccessibilityNodeInfo? = runner.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        if (node.contentDescription?.toString() == "关闭") {
            var target: AccessibilityNodeInfo = node
            while (!target.isClickable) target = target.parent ?: break
            return target.takeIf { it.isClickable }
        }
        for (i in 0 until node.childCount) findClose(node.getChild(i))?.let { return it }
        return null
    }
    try {
        runner.targetContext.startActivity(Intent(runner.targetContext, WidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        editor = runner.waitForMonitorWithTimeout(editorMonitor, 10_000) as? WidgetConfigActivity
        checkNotNull(editor) { "Editor did not open" }
        Thread.sleep(1500)
        val previous = editor
        runner.runOnMainSync { checkNotNull(editor).recreate() }
        val recreatedBy = android.os.SystemClock.uptimeMillis() + 10_000
        while (runner.widgetEditorActivity === previous && android.os.SystemClock.uptimeMillis() < recreatedBy) Thread.sleep(50)
        editor = runner.widgetEditorActivity?.takeUnless { it === previous }
        checkNotNull(editor) { "Editor did not recreate" }
        Thread.sleep(1500)
        check(!editor.isFinishing && mainMonitor.hits == 0) { "Editor forwarded to MainActivity or closed itself" }
        runner.uiAutomation.clearCache()
        check(checkNotNull(findClose()).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (!editor.isDestroyed && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
        check(editor.isDestroyed && mainMonitor.hits == 0) { "Closing editor created an extra navigation hop" }
    } finally {
        runner.runOnMainSync { editor?.takeUnless { it.isDestroyed }?.finish() }
        runner.removeMonitor(editorMonitor)
        runner.removeMonitor(mainMonitor)
    }
}
