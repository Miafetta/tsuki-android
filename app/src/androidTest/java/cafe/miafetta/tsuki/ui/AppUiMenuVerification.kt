package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.theme.TsukiAppTheme
import cafe.miafetta.tsuki.ui.components.TsukiMenu

import android.app.Activity
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.MainActivity
import top.yukonga.miuix.kmp.basic.*
import java.io.File

internal fun AppUiVerification.verifyMenuMotion(host: Activity, phase: String) {
    val folder = File(targetContext.getExternalFilesDir(null), "redesign-v4/$phase").apply { mkdirs() }
    fun record(label: String, name: String) {
        val anchor = Rect().apply { checkNotNull(find(label, exact = true, clickable = true)).getBoundsInScreen(this) }
        capture(folder, "$name-before")
        val recording = uiAutomation.executeShellCommand(
            "screenrecord --bit-rate 16000000 --time-limit 4 ${folder.absolutePath}/$name.mp4")
        try {
            frames(host, 20)
            val downTime = android.os.SystemClock.uptimeMillis()
            fun touch(action: Int) {
                val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action,
                    anchor.centerX().toFloat(), anchor.centerY().toFloat(), 0)
                    .apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
                try { check(uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            touch(android.view.MotionEvent.ACTION_DOWN)
            frames(host, 8)
            touch(android.view.MotionEvent.ACTION_UP)
            frames(host)
            verifyMenuBounds()
            capture(folder, "$name-stable")
            shell("input keyevent BACK")
            frames(host)
            assertMenu(false)
            capture(folder, "$name-dismissed")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() }
        } finally { recording.close() }
    }
    record("切换角色", "role")
    click("部件")
    framesUntil(host) { page.intValue == 1 }
    runOnMainSync { dialog.value = "widget" }
    frames(host)
    record("角色", "config-role")
    record("配色", "config-palette")
    click("关闭")
    frames(host)
    click("我的")
    framesUntil(host) { page.intValue == 2 }
    runOnMainSync {
        val list = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:2" }
        checkNotNull(list.config.getOrNull(SemanticsActions.ScrollToIndex)?.action).invoke(0)
    }
    frames(host)
    record("账号菜单", "account")
    scrollToTag(host, "settings-appearance", 2)
    record("深色模式", "theme")
}

internal fun AppUiVerification.verifyMenuInterruptions(host: MainActivity) {
    val expanded = mutableStateOf(false)
    var selections = 0
    val folder = File(targetContext.getExternalFilesDir(null), "menu-interruptions").apply { mkdirs() }
    runOnMainSync { host.setContent {
        TsukiAppTheme("LIGHT") {
            Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F2)).statusBarsPadding().padding(12.dp)) {
                Box(Modifier.align(Alignment.TopEnd)) {
                    Button(onClick = { expanded.value = true }) { Text("菜单样本") }
                    TsukiMenu(expanded.value, listOf(DropdownItem("作息管理", onClick = { selections++ }),
                        DropdownItem("设置", onClick = { selections++ })), { expanded.value = false })
                }
            }
        }
    } }
    frames(host)
    val recording = uiAutomation.executeShellCommand(
        "screenrecord --bit-rate 16000000 --time-limit 6 ${folder.absolutePath}/motion.mp4")
    try {
        frames(host, 24)
        click("菜单样本")
        frames(host)
        assertMenu(true)
        capture(folder, "open")
        shell("input keyevent BACK")
        frames(host)
        assertMenu(false)
        runOnMainSync { expanded.value = true }
        frames(host, 2)
        runOnMainSync { expanded.value = false }
        frames(host)
        assertMenu(false)
        runOnMainSync { expanded.value = true }
        frames(host, 8)
        runOnMainSync { expanded.value = false }
        frames(host, 2)
        runOnMainSync { expanded.value = true }
        frames(host)
        assertMenu(true)
        val option = checkNotNull(find("作息管理", exact = true, clickable = true))
        check(option.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        option.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        frames(host)
        assertMenu(false)
        check(selections == 1) { "Selection fired $selections times" }
        click("菜单样本")
        frames(host)
        assertMenu(true)
        shell("input tap 5 ${host.window.decorView.height / 2}")
        frames(host)
        assertMenu(false)
        check(selections == 1)
        File(folder, "result.txt").writeText("PASS: early close removes popup; interrupted exit reopens; " +
            "duplicate click commits once; outside tap and Back dismiss without selection")
    } finally { android.os.ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() } }
}

internal fun AppUiVerification.verifyMenuBounds() {
    fun visit(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
        (0 until node.childCount).flatMap { node.getChild(it)?.let(::visit).orEmpty() }
    val root = requireNotNull(uiAutomation.rootInActiveWindow)
    val window = Rect().apply { root.getBoundsInScreen(this) }
    visit(root).filter { it.isVisibleToUser && it.isClickable }.forEach { node ->
        val rect = Rect().apply { node.getBoundsInScreen(this) }
        check(window.contains(rect)) { "Menu item leaves the window: $rect / $window" }
    }
}

private fun AppUiVerification.chipBounds(host: Activity): androidx.compose.ui.geometry.Rect {
    var rect = androidx.compose.ui.geometry.Rect.Zero
    runOnMainSync {
        rect = composeNodes(host).single {
            it.config.getOrNull(SemanticsProperties.TestTag) == "game-chip:arknights"
        }.boundsInRoot
    }
    return rect
}

internal fun AppUiVerification.verifyTopRefresh(host: Activity) {
    val expandedTop = chipBounds(host).top
    runOnMainSync {
        val list = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:0" }
        checkNotNull(list.config.getOrNull(SemanticsActions.ScrollToIndex)?.action).invoke(4)
    }
    frames(host)
    val width = host.window.decorView.width
    val height = host.window.decorView.height
    shell("input swipe ${width / 2} ${height / 2} ${width / 2} ${height / 3} 400")
    frames(host)
    check(chipBounds(host).top < expandedTop) { "Fixture did not collapse the title" }
    runOnMainSync {
        val button = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "top-refresh" }
        val click = checkNotNull(composeNodes(host).firstOrNull {
            it.config.getOrNull(SemanticsActions.OnClick) != null &&
                button.boundsInRoot.contains(it.boundsInRoot.center)
        }?.config?.getOrNull(SemanticsActions.OnClick)?.action)
        click()
        click()
    }
    framesUntil(host) { refreshClicks == 1 }
    frames(host)
    check(kotlin.math.abs(chipBounds(host).top - expandedTop) < 2f) { "Title did not expand before refresh" }
    capture(File(targetContext.getExternalFilesDir(null), "redesign-v4"), "top-refresh-visible")
    runOnMainSync {
        check(composeNodes(host).any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "正在刷新" } == true &&
                node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
        }) { "Pull-to-refresh indicator did not appear" }
    }
    check(refreshClicks == 1) { "Repeated click launched duplicate refreshes" }
    runOnMainSync {
        refreshing.value = false
        canRefresh.value = true
    }
    frames(host, 180)
}
