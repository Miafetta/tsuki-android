package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.state.WidgetPreviewSource
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.state.UiPreferences
import androidx.core.content.edit
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.glass.glassSurfaceVerification

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.Settings
import android.view.Surface
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsNode
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTraining
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Exercises production pages with in-memory fixtures; never saves accounts or Widget IDs.
// This class holds the fixture state and UI helpers; the scenario steps are extensions in the AppUi*.kt files.
class AppUiVerification(internal val runner: Instrumentation) {
    internal val targetContext get() = runner.targetContext
    internal val uiAutomation get() = runner.uiAutomation
    internal val hostRunner get() = runner as WidgetRenderInstrumentation
    internal val page = mutableIntStateOf(0)
    internal val catalog = mutableIntStateOf(0)
    private val previewCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(8)
    internal val previewVersion = mutableIntStateOf(0)
    private var previewRender: (suspend (WidgetSnapshot?, Boolean, String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null
    internal fun previews() = WidgetPreviews(checkNotNull(previewRender), previewCache, previewVersion.intValue)
    internal val canRefresh = mutableStateOf(true)
    internal var refreshClicks = 0
    internal val refreshing = mutableStateOf(false)
    internal val deleting = mutableStateOf(false)
    internal val blur = mutableStateOf(true)
    internal val selectedRole = mutableIntStateOf(0)
    internal val theme = mutableStateOf("LIGHT")
    internal val dialog = mutableStateOf("")
    // Toasts the account editor asked for; the production app shows them through UiMessages.
    internal val editorMessages = java.util.concurrent.CopyOnWriteArrayList<String>()
    internal val fontScale = mutableStateOf(1f)
    internal val configBusy = mutableStateOf(false)
    internal val configTheme = mutableStateOf("LIGHT")
    internal val longNames = mutableStateOf(false)
    internal val trainingCase = mutableStateOf<WidgetTraining?>(null)
    internal val emptyState = mutableStateOf(false)
    internal val savedConfigs = mutableListOf<Pair<String, String>>()
    internal val role = UiRole("ui-fixture", "森空岛主账号", "100000001", "测试博士 · 官服", "fake.skland")
    internal lateinit var snapshot: WidgetSnapshot

    internal fun runOnMainSync(action: () -> Unit) {
        var failure: Throwable? = null
        runner.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    fun run(redesignV4: String? = null): Pair<Boolean, String> {
        var activity: MainActivity? = null
        val folder = File(targetContext.getExternalFilesDir(null), "ui-tests-native").apply { mkdirs() }
        // Restore the user's rotation lock afterwards; unfreezing alone would turn auto-rotate on. web-login-kill never
        // reaches the restore, so the pair leaves rotation alone.
        val freezeRotation = redesignV4 !in listOf("probe", "dock", "web-login-kill", "web-login-after-kill")
        val resolver = targetContext.contentResolver
        val autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        val userRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        // Fixture roles are written to the selected-role preference; put the user's choice back afterwards.
        val preferences = targetContext.getSharedPreferences(UiPreferences.FILE, 0)
        val savedRole = preferences.getString(UiPreferences.ROLE, null)
        try {
            if (freezeRotation) check(uiAutomation.setRotation(Surface.ROTATION_0))
            uiAutomation.shell("am start -n cafe.miafetta.tsuki/.MainActivity -f 0x10008000")
            check(hostRunner.uiActivityCreated.await(10, TimeUnit.SECONDS)) { "MainActivity did not start" }
            val host = checkNotNull(hostRunner.uiActivity)
            activity = host
            // HyperOS scales the window while opening it; captures must start after that animation settles.
            uiAutomation.waitForIdle(1500, 10_000)
            standaloneScenario(redesignV4, host, folder)?.let { return true to it }
            snapshot = fixtureSnapshot()
            runOnMainSync {
                previewRender = WidgetPreviewSource(host)::render
                host.setContent { FixtureApp(host, redesignV4) }
            }
            frames(host)
            redesignV4?.let { return true to fixtureScenario(it, host) }
            return true to "PASS ${fullSuite(host, folder)} native UI cases; screenshots: ${folder.absolutePath}"
        } catch (failure: Throwable) {
            uiAutomation.takeScreenshot()?.let { bitmap ->
                File(folder, "failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            return false to failure.stackTraceToString()
        } finally {
            // A scenario may have recreated the activity; finish the current one.
            (hostRunner.uiActivity ?: activity)?.let { runOnMainSync { it.finish() } }
            val deadline = System.currentTimeMillis() + 5000
            while (hostRunner.uiActivity?.isDestroyed == false && System.currentTimeMillis() < deadline) Thread.sleep(50)
            preferences.edit(commit = true) {
                if (savedRole == null) remove(UiPreferences.ROLE) else putString(UiPreferences.ROLE, savedRole)
            }
            if (freezeRotation) uiAutomation.setRotation(if (autoRotate) android.app.UiAutomation.ROTATION_UNFREEZE else userRotation)
        }
    }

    // Scenarios that drive the production app or install their own content; null means the page fixture is needed.
    private fun standaloneScenario(redesignV4: String?, host: MainActivity, folder: File): String? = when (redesignV4) {
        "widget-editor" -> {
            cafe.miafetta.tsuki.widgetEditorVerification(hostRunner)
            "PASS: editor stays open through recreation and closes without a second MainActivity"
        }
        "widget-editor-save" -> cafe.miafetta.tsuki.widgetEditorSaveVerification(hostRunner, host)
        "widget-editor-change" -> cafe.miafetta.tsuki.widgetEditorChangeVerification(hostRunner, host)
        "widget-navigation" -> {
            cafe.miafetta.tsuki.widgetNavigationVerification(hostRunner)
            "PASS: configured native/bitmap/loading and legacy cached taps open Overview; unconfigured taps open the " +
                "editor; edit entry stays separate"
        }
        "theme-header" -> {
            cafe.miafetta.tsuki.ui.glass.themeStoreGlassVerification(runner, host)
            "PASS: theme-store banner background comparison captured"
        }
        "read-lifecycle" -> {
            verifyReadAndLifecycle(host)
            "PASS: cache errors deduplicated, success/role reset, queued messages survive STOP for 5.5 seconds, " +
                "shown messages never replay, three immediate recreations deliver once"
        }
        "session-restore" -> SessionRestoreVerification(hostRunner, host).run()
        "secondary-glass" -> SecondaryGlassVerification(hostRunner, host).run()
        "dock-tone" -> dockToneVerification(hostRunner, host)
        "page-swipe" -> PageSwipeVerification(hostRunner, host).run()
        "nav-corner" -> navCornerVerification(hostRunner, host)
        // Loads the official start page (no sign-in); see WebLoginVerification.kt.
        "web-login" -> verifyWebLogin(host, File(folder.parentFile, "web-login").apply { mkdirs() })
        "web-login-kill" -> killDuringWebLogin(host, File(folder.parentFile, "web-login").apply { mkdirs() })
        "web-login-after-kill" -> verifyWipedAfterKill()
        "errors-before", "errors" -> {
            verifyErrorMessages(host, folder)
            "PASS: refresh failure, page switch and sheet dismissal; Toast event counts"
        }
        else -> null
    }

    // Named scenarios that run on the page fixture; the full suite runs when no scenario is named.
    private fun fixtureScenario(redesignV4: String, host: MainActivity): String = when (redesignV4) {
        "top-refresh" -> {
            verifyTopRefresh(host)
            "PASS: top refresh returns list/header to top, shows pull indicator, and ignores repeated clicks"
        }
        "visual" -> {
            verifyGlassCaptures(host)
            "PASS: light/dark stationary and collapsed main pages, glass probes"
        }
        "menu" -> {
            for (palette in listOf("LIGHT", "DARK")) {
                runOnMainSync {
                    theme.value = palette
                    page.intValue = 0
                }
                frames(host)
                verifyMenuMotion(host, "menu-${palette.lowercase()}")
            }
            verifyMenuInterruptions(host)
            "PASS: five menus in light/dark; real touch open, Back dismissal, safe bounds; " +
                "early dismissal, reopen, outside tap, single selection"
        }
        "dock" -> {
            verifyDockMotion(host)
            "PASS: dock real-touch selection, interruption, cancel, geometry, scroll retention; " +
                "light/dark, blur on/off, font 1/1.3"
        }
        "probe" -> {
            glassSurfaceVerification(runner, host, File(targetContext.getExternalFilesDir(null), "redesign-v4").apply { mkdirs() })
            "PASS: stationary glass probe and secondary page captures"
        }
        else -> {
            verifyMenuMotion(host, redesignV4)
            if (redesignV4 != "baseline") verifyGlassCaptures(host)
            val checked = if (redesignV4 == "baseline") "baseline" else "light/dark glass states and fallback"
            "PASS: five real-touch menus; $checked"
        }
    }

    internal fun shell(command: String) = uiAutomation.shell(command)

    internal fun composeNodes(host: Activity): List<SemanticsNode> = host.semanticsNodes()

    internal fun menuNode(): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.viewIdResourceName == "tsuki-menu" && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { found -> return found } }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::visit)
    }

    internal fun assertMenu(open: Boolean) {
        check((menuNode() != null) == open) { "Menu visibility expected $open" }
    }

    internal fun frames(activity: Activity, count: Int = 72) {
        // Live backdrop drawing can keep the queue active; synchronize on rendered frames instead.
        val done = CountDownLatch(1)
        runOnMainSync {
            var remaining = count
            val view = activity.window.decorView
            val tick = object : Runnable { override fun run() {
                if (--remaining == 0) done.countDown()
                else view.postOnAnimation(this)
            } }
            view.postOnAnimation(tick)
        }
        check(done.await(5, TimeUnit.SECONDS)) { "UI frame timeout" }
        uiAutomation.clearCache()
    }

    internal fun framesUntil(activity: Activity, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        do { frames(activity, 6) } while (!condition() && android.os.SystemClock.uptimeMillis() < deadline)
        check(condition()) { "UI did not reach the requested state" }
    }

    internal fun find(text: String, exact: Boolean = false, clickable: Boolean = false, heading: Boolean = false,
        progress: Boolean = false): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (!node.isVisibleToUser) return null
            val progressMatches = !progress || node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            if ((!heading || node.isHeading) && progressMatches &&
                listOfNotNull(node.text, node.contentDescription).any { if (exact) it.toString() == text else it.contains(text) }) {
                if (!clickable) return node
                var target: AccessibilityNodeInfo? = node
                while (target != null) {
                    target.refresh()
                    if (target.isClickable) return target
                    target = target.parent
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it)?.let { found -> return found } }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::visit)
    }

    internal fun click(text: String) {
        val node = requireNotNull(find(text, exact = true, clickable = true)) { "Cannot find clickable $text" }
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    // Injects a touch at the node centre so hit testing, not just the accessibility action, is exercised.
    internal fun tap(text: String) {
        val node = requireNotNull(find(text, exact = true, clickable = true)) { "Cannot find $text" }
        val bounds = Rect().apply { node.getBoundsInScreen(this) }
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
    }

    internal fun capture(folder: File, name: String) {
        val bitmap = requireNotNull(uiAutomation.takeScreenshot())
        check(bitmap.width > 0 && bitmap.height > 0)
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
