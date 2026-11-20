package cafe.miafetta.tsuki.ui

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.StorageContract
import cafe.miafetta.tsuki.core.SnapshotJson
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.ui.state.AppState
import cafe.miafetta.tsuki.ui.state.ConfigSheet
import cafe.miafetta.tsuki.ui.state.UiNotifier
import cafe.miafetta.tsuki.ui.state.UiPreferences
import cafe.miafetta.tsuki.ui.state.UiSession
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import java.util.concurrent.atomic.AtomicInteger

// Runs the real MainActivity content over an in-memory AppState; never saves accounts, widgets or pin requests.
internal class SessionRestoreVerification(private val runner: WidgetRenderInstrumentation, host: MainActivity) {
    private val reads = AtomicInteger()
    private val snapshot = SnapshotJson.write(WidgetSnapshot("测试博士", listOf(WidgetMetric("理智", 1, 2)),
        System.currentTimeMillis())).toString()
    private val fixture = AppState(host.applicationContext, UiNotifier {}) { method, _ ->
        Bundle().apply {
            if (method == StorageContract.OVERVIEW) putString(StorageContract.JSON, OVERVIEW)
            if (method == StorageContract.DASHBOARD) {
                reads.incrementAndGet()
                putString(StorageContract.SNAPSHOT, snapshot)
            }
        }
    }
    private var current = host

    fun run(): String {
        val preferences = current.getSharedPreferences(UiPreferences.FILE, 0)
        val savedRole = preferences.getString(UiPreferences.ROLE, null)
        try {
            val host = current
            runner.runOnMainSync {
                host.viewModelStore.clear()
                ViewModelProvider(host, viewModelFactory { initializer { fixture } })[AppState::class.java]
            }
            recreate()
            waitFor("Fixture roles did not load") { fixture.roles.size == 2 && reads.get() > 0 }
            repeat(2) { round ->
                val count = foregroundReads()
                check(count == 1) { "Foreground $round read the overview cache $count times" }
            }
            verifyRecreation()
            verifySaver()
            return "PASS: two foreground resumes read the overview cache once each; recreation keeps page, selected role and " +
                "open config sheet; UiSession restores catalog, edit sheet, pending pin, permission dialog and settings wait"
        } finally {
            runner.uiContentOnCreate = null
            val last = current
            runner.runOnMainSync { last.finish() }
            val deadline = System.currentTimeMillis() + 5000
            while (!last.isDestroyed && System.currentTimeMillis() < deadline) Thread.sleep(50)
            preferences.edit(commit = true) {
                if (savedRole == null) remove(UiPreferences.ROLE) else putString(UiPreferences.ROLE, savedRole)
            }
        }
    }

    private fun foregroundReads(): Int {
        runner.runOnMainSync { check(current.moveTaskToBack(true)) }
        waitFor("Activity did not STOP") { !current.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        Thread.sleep(500)
        val before = reads.get()
        runner.uiAutomation.shell("am start -n cafe.miafetta.tsuki/.MainActivity -f 0x10200000")
        waitFor("Activity did not resume") { current.lifecycle.currentState == Lifecycle.State.RESUMED }
        settle()
        check(runner.uiActivity === current) { "Foreground created another MainActivity" }
        return reads.get() - before
    }

    private fun verifyRecreation() {
        check(selectedTab() == "nav-tab:0") { "Fixture did not start on Overview" }
        click("切换角色")
        click("第二角色")
        check(showsText("UID 2")) { "Role switch did not apply" }
        click("部件")
        click("添加")
        check(selectedTab() == "nav-tab:1" && sheetOpen()) { "Config sheet did not open on Widgets" }
        recreate()
        check(selectedTab() == "nav-tab:1") { "Page lost on recreation" }
        check(sheetOpen()) { "Config sheet lost on recreation" }
        // Add opens the new-widget sheet, whose head has 返回.
        click("返回")
        check(!sheetOpen()) { "Config sheet did not close" }
        click("概览")
        check(showsText("UID 2")) { "Selected role lost on recreation" }
    }

    // Covers the saved fields the production flow cannot reach without requesting a real pin.
    private fun verifySaver() {
        var restored: UiSession? = null
        runner.uiContentOnCreate = { activity ->
            activity.setContent {
                key("session-saver") {
                    restored = rememberSaveable(saver = UiSession.Saver) { UiSession(0, "", ConfigSheet.Closed()) }
                }
            }
        }
        runner.runOnMainSync { runner.uiContentOnCreate?.invoke(current) }
        settle()
        val original = checkNotNull(restored)
        runner.runOnMainSync {
            original.page = 2
            original.catalog = 1
            original.selectedKey = "$ACCOUNT/2"
            original.configSheet = ConfigSheet.Edit(42, "DARK")
            original.pendingPin = Bundle().apply {
                putString(StorageContract.THEME, "DARK")
                putString(StorageContract.CHARACTER_ID, "2")
            }
            original.permissionDialog = true
            original.waitingSettings = true
        }
        settle()
        recreate()
        val failure = onMain {
            val session = checkNotNull(restored)
            val pin = session.pendingPin
            when {
                session === original -> "Session survived without saved state"
                session.page != 2 || session.catalog != 1 || session.selectedKey != "$ACCOUNT/2" -> "Page or role lost"
                session.configSheet != ConfigSheet.Edit(42, "DARK") -> "Config sheet lost: ${session.configSheet}"
                pin?.getString(StorageContract.THEME) != "DARK" || pin.getString(StorageContract.CHARACTER_ID) != "2" ->
                    "Pending pin lost"
                !session.permissionDialog || !session.waitingSettings -> "Permission dialog state lost"
                else -> null
            }
        }
        check(failure == null) { "UiSession restore failed: $failure" }
    }

    private fun recreate() {
        val previous = current
        runner.runOnMainSync { previous.recreate() }
        val deadline = System.currentTimeMillis() + 10_000
        while (runner.uiActivity === previous && System.currentTimeMillis() < deadline) Thread.sleep(50)
        current = checkNotNull(runner.uiActivity).also { check(it !== previous) { "Activity did not recreate" } }
        settle()
    }

    private fun <T> onMain(block: () -> T): T {
        var value: Result<T>? = null
        runner.runOnMainSync { value = runCatching(block) }
        return checkNotNull(value).getOrThrow()
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!onMain(condition) && System.currentTimeMillis() < deadline) Thread.sleep(50)
        check(onMain(condition)) { message }
    }

    private fun settle() = runner.settleAccessibility()

    private fun semantics(): List<SemanticsNode> = current.semanticsNodes()

    private fun selectedTab() = onMain { current.selectedNavTab().takeIf { it >= 0 }?.let { "nav-tab:$it" } }

    private fun showsText(text: String) = onMain {
        semantics().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(text) } == true }
    }

    private fun sheetOpen() = onMain { semantics().any { it.config.getOrNull(SemanticsProperties.TestTag) == "config-action" } }

    private fun click(label: String) {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || !node.isVisibleToUser) return null
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target != null) return target
            }
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        val node = checkNotNull(find(runner.uiAutomation.rootInActiveWindow)) { "Missing clickable $label" }
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $label" }
        settle()
    }

    private companion object {
        const val ACCOUNT = "session-fixture"
        val OVERVIEW = """{"accounts":[{"id":"$ACCOUNT","name":"会话测试账号","roles":[""" +
            """{"id":"1","label":"测试博士 · 官服"},{"id":"2","label":"第二角色 · 官服"}]}],"widgets":[]}"""
    }
}
