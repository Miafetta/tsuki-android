package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.sheets.ConfigEditor
import cafe.miafetta.tsuki.ui.state.AppState
import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme

import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.state.WidgetPreviewSource
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation

internal fun windowLifecycleVerification(runner: WidgetRenderInstrumentation): Pair<Boolean, String> =
    WindowLifecycleCheck(runner).run()

// Recreates MainActivity and checks what must survive (ViewModel, busy state, palette, list position, account name) and
// what must not (the credential). Content comes from in-memory fixtures; nothing is saved.
private class WindowLifecycleCheck(private val runner: WidgetRenderInstrumentation) {
    private val ui = runner.uiAutomation
    private var current: MainActivity? = null

    fun run(): Pair<Boolean, String> {
        try {
            ui.shell("am start -n cafe.miafetta.tsuki/.MainActivity -f 0x10008000")
            check(runner.uiActivityCreated.await(10, java.util.concurrent.TimeUnit.SECONDS))
            current = checkNotNull(runner.uiActivity)
            settle()
            viewModelRetention()
            configEditorState()
            accountDraftState()
            return true to "PASS: normal portrait recreation retains ViewModel, busy state, palette, list position and account name; " +
                "credential cleared; no account saved"
        } catch (failure: Throwable) {
            ui.takeScreenshot()?.let { bitmap ->
                java.io.File(runner.targetContext.getExternalFilesDir(null), "lifecycle-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            return false to failure.stackTraceToString()
        }
        finally {
            runner.uiContentOnCreate = null
            current?.let { runner.runOnMainSync { it.finish() } }
        }
    }

    private fun settle() {
        runner.waitForIdleSync()
        Thread.sleep(750)
        ui.clearCache()
    }

    private fun find(text: String, node: AccessibilityNodeInfo? = ui.rootInActiveWindow,
        clickable: Boolean = false): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) {
            if (!clickable) return node
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable) return target
                target = target.parent
            }
        }
        for (i in 0 until node.childCount) find(text, node.getChild(i), clickable)?.let { return it }
        return null
    }

    private fun click(text: String) {
        val node = checkNotNull(find(text, clickable = true)) { "Missing clickable $text" }
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "Cannot click $text: class=${node.className}, enabled=${node.isEnabled}, visible=${node.isVisibleToUser}, " +
                "actions=${node.actionList}"
        }
        settle()
    }

    private fun recreate() {
        val previous = checkNotNull(current)
        runner.runOnMainSync { previous.recreate() }
        val deadline = System.currentTimeMillis() + 10_000
        while (runner.uiActivity === previous && System.currentTimeMillis() < deadline) Thread.sleep(50)
        current = checkNotNull(runner.uiActivity)
        check(current !== previous)
        settle()
    }

    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) scrollable(node.getChild(i))?.let { return it }
        return null
    }

    private fun editable(password: Boolean, node: AccessibilityNodeInfo? = ui.rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isPassword == password) return node
        for (i in 0 until node.childCount) editable(password, node.getChild(i))?.let { return it }
        return null
    }

    private fun viewModelRetention() {
        lateinit var retained: AppState
        runner.runOnMainSync {
            retained = ViewModelProvider(checkNotNull(current))[AppState::class.java]
            retained.busy = true
        }
        recreate()
        runner.runOnMainSync {
            check(ViewModelProvider(checkNotNull(current))[AppState::class.java] === retained && retained.busy)
            retained.busy = false
        }
    }

    private fun configEditorState() {
        val roles = listOf(UiRole("fixture", "测试账号", "fixture-role", "测试角色", FakeArknightsAdapter.ID))
        val cache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(8)
        runner.uiContentOnCreate = { host -> host.setContent {
            key("window-lifecycle-config") {
            var page by rememberSaveable { mutableIntStateOf(1) }
            var editor by rememberSaveable { mutableStateOf(true) }
            TsukiAppTheme("LIGHT") {
                AppScaffold(page, onNavigate = { page = it }, onAdd = { editor = true }, overlays = {
                    ConfigEditor(host, roles, null, false, "", { editor = false }, {}, { _, _, _ -> },
                        WidgetPreviews(WidgetPreviewSource(host)::render, cache, 0), previewSnapshot = { null }, show = editor)
                }) { items(40) { top.yukonga.miuix.kmp.basic.Text("状态恢复样本 $it", Modifier.height(80.dp)) } }
            }
            }
        } }
        runner.runOnMainSync { runner.uiContentOnCreate?.invoke(checkNotNull(current)) }
        settle()
        click("配色")
        click("深色")
        check(find("深色") != null)
        recreate()
        check(find("深色") != null) { "Widget palette lost on recreation" }
        ui.shell("input keyevent BACK")
        settle()
        check(checkNotNull(scrollable(ui.rootInActiveWindow)).performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        settle()
        val firstVisible = (0..39).first { find("状态恢复样本 $it")?.isVisibleToUser == true }
        check(firstVisible > 0)
        click("概览")
        click("部件")
        check(find("状态恢复样本 $firstVisible")?.isVisibleToUser == true) { "Per-page list position lost after a tab round trip" }
        recreate()
        check(find("状态恢复样本 $firstVisible")?.isVisibleToUser == true) { "List position lost on recreation" }
    }

    private fun accountDraftState() {
        runner.uiContentOnCreate = { host -> host.setContent {
            key("window-lifecycle-account") {
                TsukiAppTheme("LIGHT") { AccountNavigationFixture(host, true, {}, background = {}) }
            }
        } }
        runner.runOnMainSync { runner.uiContentOnCreate?.invoke(checkNotNull(current)) }
        settle()
        fun enter(password: Boolean, value: String) {
            check(checkNotNull(editable(password)).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
            settle()
        }
        enter(false, "重建测试")
        enter(true, "synthetic-credential")
        check(checkNotNull(editable(true)).text?.isNotEmpty() == true)
        click("平台")
        check(find("选择平台") != null)
        click("返回")
        check(checkNotNull(editable(false)).text?.toString() == "重建测试")
        check(checkNotNull(editable(true)).text?.isNotEmpty() == true) { "Credential lost during platform selection" }
        recreate()
        check(checkNotNull(editable(false)).text?.toString() == "重建测试") { "Account name lost on recreation" }
        check(checkNotNull(editable(true)).text.isNullOrEmpty()) { "Credential entered saved state" }
    }
}
