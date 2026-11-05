package cafe.miafetta.tsuki

import cafe.miafetta.tsuki.ui.shell
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import cafe.miafetta.tsuki.core.FileWidgetRepository
import cafe.miafetta.tsuki.widget.StatusWidgetProvider
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Launches the editor for a result like a launcher does; saving keeps the instance's existing role and theme.
internal fun widgetEditorSaveVerification(runner: WidgetRenderInstrumentation, host: MainActivity): String {
    val context = runner.targetContext
    val id = StatusWidgetProvider.owned(context).firstOrNull { readConfig(runner, it) != null }
        ?: error("Requires an existing configured widget; this test never creates one")
    val before = checkNotNull(readConfig(runner, id))
    val saved = editorResult(runner, host, id) { clickLabel(runner, "保存") }
    check(saved.resultCode == Activity.RESULT_OK) { "Save returned ${saved.resultCode}" }
    check(saved.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) == id) { "Save did not return the widget id" }
    check(readConfig(runner, id) == before) { "Saving an unchanged editor changed the stored configuration" }
    val closed = editorResult(runner, host, id) { clickLabel(runner, "关闭") }
    check(closed.resultCode == Activity.RESULT_CANCELED) { "Close returned ${closed.resultCode}" }
    val back = editorResult(runner, host, id) { shell(runner, "input keyevent BACK") }
    check(back.resultCode == Activity.RESULT_CANCELED) { "Back returned ${back.resultCode}" }
    check(readConfig(runner, id) == before) { "Cancelling changed the stored configuration" }
    return "PASS: unchanged save returns RESULT_OK with the widget id and keeps the stored config; close and Back return RESULT_CANCELED"
}

// readConfig, editorResult and clickLabel are shared with WidgetEditorChangeVerification.
internal fun readConfig(runner: WidgetRenderInstrumentation, id: Int) =
    FileWidgetRepository(File(runner.targetContext.noBackupFilesDir, "widgets")).read(id)?.config

internal fun editorResult(runner: WidgetRenderInstrumentation, host: MainActivity, id: Int, action: () -> Unit): ActivityResult {
    val done = CountDownLatch(1)
    var result: ActivityResult? = null
    val editorMonitor = runner.addMonitor(WidgetConfigActivity::class.java.name, null, false)
    val mainMonitor = runner.addMonitor(MainActivity::class.java.name, null, false)
    var launcher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
    try {
        runner.runOnMainSync {
            launcher = host.activityResultRegistry.register("widget-editor-save", ActivityResultContracts.StartActivityForResult()) {
                result = it
                done.countDown()
            }
            checkNotNull(launcher).launch(Intent(host, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }
        val editor = runner.waitForMonitorWithTimeout(editorMonitor, 10_000)
        checkNotNull(editor) { "Editor did not open" }
        awaitLabel(runner, "保存")
        action()
        check(done.await(10, TimeUnit.SECONDS)) { "Editor returned no result" }
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (!editor.isDestroyed && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
        check(editor.isDestroyed && mainMonitor.hits == 0) { "Editor left an extra navigation hop" }
        return checkNotNull(result)
    } finally {
        runner.runOnMainSync { launcher?.unregister() }
        runner.removeMonitor(editorMonitor)
        runner.removeMonitor(mainMonitor)
    }
}

private fun findLabel(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
    if (node == null || !node.isVisibleToUser) return null
    if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target != null && target.isEnabled) return target
    }
    for (index in 0 until node.childCount) findLabel(node.getChild(index), label)?.let { return it }
    return null
}

private fun awaitLabel(runner: WidgetRenderInstrumentation, label: String): AccessibilityNodeInfo {
    val deadline = android.os.SystemClock.uptimeMillis() + 10_000
    while (android.os.SystemClock.uptimeMillis() < deadline) {
        runner.uiAutomation.clearCache()
        findLabel(runner.uiAutomation.rootInActiveWindow, label)?.let { return it }
        Thread.sleep(100)
    }
    error("Missing enabled $label")
}

internal fun clickLabel(runner: WidgetRenderInstrumentation, label: String) {
    // Let the sheet finish opening so the click reaches a settled action.
    Thread.sleep(1200)
    check(awaitLabel(runner, label).performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $label" }
}

private fun shell(runner: WidgetRenderInstrumentation, command: String) {
    Thread.sleep(1200)
    runner.uiAutomation.shell(command)
}
