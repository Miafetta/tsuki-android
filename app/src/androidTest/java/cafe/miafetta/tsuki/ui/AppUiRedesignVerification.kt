package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.theme.TsukiTextStyles

import android.app.Activity
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import cafe.miafetta.tsuki.core.buildingLabels
import cafe.miafetta.tsuki.core.realtimeLabels
import java.io.File

// Captures the redesign reference states and checks every menu against the safe window; returns its case count.
internal fun AppUiVerification.verifyRedesign(host: Activity): Int {
    val folder = File(targetContext.getExternalFilesDir(null), "redesign-v4/full-suite").apply { mkdirs() }
    assertTopInset(host)
    verifyOverviewSpacing(host)
    capture(folder, "01-overview-light")
    capture(folder, "14-dock-idle-light")
    redesignRoleMenu(host, folder)
    redesignOverviewStates(host, folder)
    redesignConfigMenus(host, folder)
    redesignMePage(host, folder)
    redesignEmptyPages(host, folder)
    File(folder, "result.txt").writeText("PASS: four menus opened, fit safe window, selected and closed; theme DARK; " +
        "delete confirmation cancelled; role switched; palette DARK preview; top actions below status bars.")
    return 6
}

private fun AppUiVerification.assertMenuInside(host: Activity): Rect {
    val bounds = Rect().apply { checkNotNull(menuNode()).getBoundsInScreen(this) }
    val insets = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars())
    val decor = host.window.decorView
    val window = Rect(insets.left, insets.top, decor.width - insets.right, decor.height - insets.bottom)
    check(window.contains(bounds)) { "Menu outside safe window: $bounds / $window" }
    return bounds
}

private fun AppUiVerification.scrollToStart(host: Activity, index: Int) {
    runOnMainSync {
        val list = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:$index" }
        checkNotNull(list.config.getOrNull(SemanticsActions.ScrollToIndex)?.action).invoke(0)
    }
    frames(host)
}

private fun AppUiVerification.assertTopInset(host: Activity) {
    val top = Rect().apply { checkNotNull(find("刷新", exact = true)).getBoundsInScreen(this) }.top
    val inset = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
    check(top >= inset) { "Refresh overlaps status bar: $top < $inset" }
}

private fun AppUiVerification.redesignRoleMenu(host: Activity, folder: File) {
    click("切换角色")
    frames(host)
    assertMenu(true)
    assertMenuInside(host)
    check(find("测试博士", exact = true) != null && find("第二角色", exact = true) != null)
    fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
        (0 until node.childCount).flatMap { node.getChild(it)?.let(::descendants).orEmpty() }
    val menuItems = descendants(checkNotNull(menuNode()))
    File(folder, "menu-accessibility.txt").writeText(menuItems.joinToString("\n") {
        "${it.className}: ${it.text} / ${it.contentDescription}; " +
            "clickable=${it.isClickable}, selected=${it.isSelected}, checked=${it.checkedState()}"
    })
    check(menuItems.any { it.isSelected || it.checkedState() }) { "Selected role missing accessibility state" }
    val service = uiAutomation.serviceInfo
    service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    uiAutomation.serviceInfo = service
    check(uiAutomation.windows.any { it.isFocused && it.root?.packageName == targetContext.packageName }) { "Menu window has no focus" }
    capture(folder, "12-role-menu-light")
    shell("input keyevent KEYCODE_BACK")
    frames(host)
    assertMenu(false)
    click("切换角色")
    frames(host)
    assertMenu(true)
    shell("input tap 5 ${host.window.decorView.height / 2}")
    frames(host)
    assertMenu(false)
    click("切换角色")
    frames(host)
    assertMenu(true)
    click("第二角色")
    frames(host)
    assertMenu(false)
    check(selectedRole.intValue == 1)
    click("切换角色")
    frames(host)
    click("测试博士")
    frames(host)
    assertMenu(false)
}

private fun AppUiVerification.redesignOverviewStates(host: Activity, folder: File) {
    val width = host.window.decorView.width
    val height = host.window.decorView.height
    shell("input swipe ${width / 2} ${height * 3 / 4} ${width / 2} ${height / 3} 500")
    frames(host)
    assertTopInset(host)
    capture(folder, "02-overview-scrolled-light")
    shell("input swipe ${width / 2} ${height / 2} ${width / 2} ${height / 2 - height / 14} 350")
    frames(host)
    capture(folder, "16-dock-scrolled-light")
    scrollToStart(host, 0)
    shell("input swipe ${width / 2} ${height / 3} ${width / 2} ${height * 2 / 3} 500")
    frames(host)
    runOnMainSync { refreshing.value = true }
    frames(host)
    capture(folder, "03-overview-refreshing-light")
    capture(folder, "15-dock-refreshing-light")
    runOnMainSync { refreshing.value = false }
    frames(host)
}

private fun AppUiVerification.redesignConfigMenus(host: Activity, folder: File) {
    click("部件")
    framesUntil(host) { page.intValue == 1 }
    capture(folder, "04-widgets-light")
    runOnMainSync {
        dialog.value = "widget-new"
        configTheme.value = "LIGHT"
    }
    frames(host)
    check(find("测试博士。", exact = false) != null) { "New config preview did not use the overview snapshot" }
    capture(folder, "05-config-light")
    click("角色")
    frames(host)
    assertMenu(true)
    assertMenuInside(host)
    click("第二角色")
    frames(host)
    check(find("未同步角色。", exact = false) != null) { "Overview snapshot leaked into another role" }
    click("角色")
    frames(host)
    click("测试博士")
    frames(host)
    val anchor = Rect().apply { checkNotNull(find("配色", exact = true, clickable = true)).getBoundsInScreen(this) }
    click("配色")
    frames(host)
    assertMenu(true)
    val menu = assertMenuInside(host)
    // Field menus reach 14dp past the value's centre line and align with its end (the row's 16dp inner margin).
    val dp = host.resources.displayMetrics.density
    check(menu.top < anchor.top && kotlin.math.abs(menu.bottom - anchor.centerY() - 14 * dp) <= 2 * dp &&
        kotlin.math.abs(anchor.right - menu.right - 16 * dp) <= 2 * dp) {
        "Bottom menu did not open upward over the value: $menu / $anchor"
    }
    capture(folder, "13-palette-menu-light")
    click("深色")
    frames(host)
    assertMenu(false)
    runOnMainSync { check(composeNodes(host).any { it.config.getOrNull(SemanticsProperties.TestTag) == "config-preview:DARK" }) }
    click("关闭")
    frames(host)
}

private fun AppUiVerification.redesignMePage(host: Activity, folder: File) {
    click("我的")
    framesUntil(host) { page.intValue == 2 }
    capture(folder, "06-me-light")
    click("账号菜单")
    frames(host)
    assertMenu(true)
    assertMenuInside(host)
    check(find("更新 Cred", exact = true) != null && find("删除账号", exact = true) != null)
    capture(folder, "11-account-menu-light")
    click("删除账号")
    frames(host)
    assertMenu(false)
    check(deleting.value && find("删除账号", exact = true) != null && find("删除", exact = true) != null)
    capture(folder, "11b-delete-confirmation-light")
    click("取消")
    frames(host)
    check(!deleting.value)
    scrollToTag(host, "settings-appearance", 2)
    click("深色模式")
    frames(host)
    assertMenu(true)
    assertMenuInside(host)
    check(find("开启", exact = true) != null)
    capture(folder, "10-theme-menu-light")
    click("开启")
    frames(host)
    assertMenu(false)
    check(theme.value == "DARK")
    capture(folder, "17-me-dark")
    click("概览")
    framesUntil(host) { page.intValue == 0 }
    capture(folder, "18-overview-dark")
    runOnMainSync {
        theme.value = "LIGHT"
        page.intValue = 2
    }
    frames(host)
    click("添加")
    frames(host)
    capture(folder, "07-account-editor-light")
    click("返回")
    frames(host)
}

private fun AppUiVerification.redesignEmptyPages(host: Activity, folder: File) {
    runOnMainSync {
        emptyState.value = true
        page.intValue = 0
    }
    frames(host)
    scrollToStart(host, 0)
    capture(folder, "08-overview-empty-light")
    click("我的")
    framesUntil(host) { page.intValue == 2 }
    scrollToStart(host, 2)
    capture(folder, "09-me-empty-light")
    runOnMainSync {
        emptyState.value = false
        page.intValue = 0
    }
    frames(host)
    scrollToStart(host, 0)
}

internal fun AppUiVerification.verifyOverviewSpacing(host: Activity) = runOnMainSync {
    val nodes = composeNodes(host)
    val chip = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "game-chip:arknights" }.boundsInRoot
    val card = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "role-card" }.boundsInRoot
    val title = nodes.single { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        layouts.any { it.layoutInput.text.text == "概览" && it.layoutInput.style.fontSize == TsukiTextStyles.title1.fontSize }
    }.boundsInRoot
    val density = host.resources.displayMetrics.widthPixels / 360f
    check(kotlin.math.abs(chip.top - title.bottom - 8 * density) <= 2) { "Title/chip gap: ${chip.top - title.bottom}" }
    check(kotlin.math.abs(card.top - chip.bottom - 20 * density) <= 2) { "Chip/card gap: ${card.top - chip.bottom}" }
}

internal fun AppUiVerification.verifyBlurLayout(host: Activity) {
    fun bounds(): Map<String, androidx.compose.ui.geometry.Rect> {
        var result = emptyMap<String, androidx.compose.ui.geometry.Rect>()
        runOnMainSync { result = composeNodes(host).mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)
                ?.takeIf { it in listOf("nav-dock", "page-list:2", "settings-appearance") }
                ?.let { it to node.boundsInRoot }
        }.toMap() }
        return result
    }
    val before = bounds()
    click("界面模糊效果")
    frames(host)
    check(!blur.value && before == bounds()) { "Disabling blur changed layout" }
    click("界面模糊效果")
    frames(host)
    check(blur.value && before == bounds()) { "Enabling blur changed layout" }
}

internal fun AppUiVerification.verifyMetricOrder(host: Activity) {
    val encountered = linkedSetOf<String>()
    scrollToStart(host, 0)
    repeat(20) {
        runOnMainSync {
            val nodes = composeNodes(host)
            val list = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:0" }
            nodes.filter {
                it.layoutInfo.isPlaced && it.positionInRoot.y >= list.boundsInRoot.top &&
                    it.positionInRoot.y < list.boundsInRoot.bottom
            }.sortedBy { it.positionInRoot.y }.forEach { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith("metric-card:") }
                    ?.removePrefix("metric-card:")?.let(encountered::add)
            }
            list.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, list.boundsInRoot.height / 3)
        }
        frames(host, 6)
    }
    check(encountered.toList() == realtimeLabels + buildingLabels) { "Dashboard order changed: $encountered" }
    scrollToStart(host, 0)
}

// AccessibilityNodeInfo.isChecked is deprecated from API 36 in favour of the tri-state getChecked().
private fun AccessibilityNodeInfo.checkedState(): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= 36) checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
    else @Suppress("DEPRECATION") isChecked
