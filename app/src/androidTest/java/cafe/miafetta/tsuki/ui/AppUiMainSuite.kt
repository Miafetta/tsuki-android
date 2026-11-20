package cafe.miafetta.tsuki.ui

import android.app.Activity
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import cafe.miafetta.tsuki.MainActivity
import java.io.File

// The full native suite that runs when no scenario is named; returns the number of verified cases.
internal fun AppUiVerification.fullSuite(host: MainActivity, folder: File): Int {
    var cases = verifyRedesign(host)
    click("切换角色")
    frames(host)
    verifyMenuBounds()
    click("第二角色")
    frames(host)
    check(selectedRole.intValue == 1)
    click("切换角色")
    frames(host)
    click("测试博士")
    frames(host)
    check(selectedRole.intValue == 0)
    cases++
    verifyMetricOrder(host)
    cases++
    for (palette in listOf("LIGHT", "DARK")) cases += paletteCases(host, folder, palette)
    for (scale in listOf(1f, 1.3f)) cases += largeFontCases(host, folder, scale)
    return cases + emptyStateCases(host, folder)
}

private fun AppUiVerification.paletteCases(host: MainActivity, folder: File, palette: String): Int {
    var cases = 0
    runOnMainSync {
        theme.value = palette
        page.intValue = 0
        catalog.intValue = 0
    }
    frames(host)
    for ((label, target) in listOf("部件" to 1, "我的" to 2, "概览" to 0)) {
        click(label)
        framesUntil(host) { page.intValue == target }
        check(page.intValue == target) { "Navigation did not select $label" }
        cases++
    }
    verifyRefreshButton(host)
    cases++
    click("部件")
    framesUntil(host) { page.intValue == 1 }
    capture(folder, "widget-${palette.lowercase()}")
    cases++
    verifyWidgetConfig(host, folder, palette)
    cases++
    verifyAccountEditor(host, folder, palette)
    cases++
    verifyAppearanceSettings(host, palette)
    cases++
    verifyBlurLayout(host)
    cases++
    capture(folder, palette.lowercase() + "-accounts")
    return cases
}

private fun AppUiVerification.verifyRefreshButton(host: MainActivity) {
    val before = refreshClicks
    runOnMainSync { canRefresh.value = false }
    frames(host)
    check(find("刷新", exact = true) != null)
    find("刷新", exact = true, clickable = true)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    frames(host)
    check(refreshClicks == before) { "Disabled refresh accepted a click" }
    runOnMainSync { canRefresh.value = true }
    frames(host)
    click("刷新")
    frames(host)
    check(refreshClicks == before + 1)
}

private fun AppUiVerification.verifyWidgetConfig(host: MainActivity, folder: File, palette: String) {
    // A real touch: an invisible overlay once swallowed taps that accessibility actions bypassed.
    tap("全部小部件")
    frames(host)
    check(catalog.intValue == 1)
    runOnMainSync {
        dialog.value = "widget-new"
        configTheme.value = "DARK"
    }
    frames(host)
    // The new-widget sheet's main button.
    val add = "添加到桌面"
    check(find("深色", exact = true) != null)
    check(find(add, exact = true, clickable = true)?.isEnabled == true)
    val navBottom = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
    checkDialogAction(host, add, host.window.decorView.height - navBottom)
    verifySheetRows(host)
    capture(folder, "config-${palette.lowercase()}")
    for ((menu, choice) in listOf("配色" to "浅色", "配色" to "深色", "角色" to "第二角色")) {
        click(menu)
        frames(host)
        if (choice != "深色") verifyMenuBounds()
        click(choice)
        frames(host)
    }
    runOnMainSync { configBusy.value = true }
    frames(host)
    val savedBefore = savedConfigs.size
    check(find(add, exact = true) != null)
    find(add, exact = true, clickable = true)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    frames(host)
    check(savedConfigs.size == savedBefore) { "Busy configuration accepted a save" }
    shell("input keyevent BACK")
    frames(host)
    check(dialog.value == "widget-new") { "Busy configuration dismissed" }
    runOnMainSync { configBusy.value = false }
    frames(host)
    click(add)
    frames(host)
    check(savedConfigs.last() == role.copy(id = "100000002", label = "第二角色").key to "DARK")
    check(dialog.value.isEmpty())
}

private fun AppUiVerification.verifyAccountEditor(host: MainActivity, folder: File, palette: String) {
    runOnMainSync { page.intValue = 2 }
    frames(host)
    click("添加")
    frames(host)
    check(find("账号信息", exact = true) != null && find("授权信息", exact = true) != null)
    capture(folder, "account-editor-${palette.lowercase()}")
    click("平台")
    frames(host)
    check(find("选择平台", exact = true) != null)
    capture(folder, "platform-${palette.lowercase()}")
    click("返回")
    frames(host)
    click("在森空岛网页登录")
    // The first WebView in a process loads the provider, which can hold the transition for a moment.
    framesUntil(host) { find("网页登录", exact = true) != null }
    val bar = Rect().apply { checkNotNull(find("网页登录", exact = true)).getBoundsInScreen(this) }
    val statusBar = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
    check(bar.top >= statusBar) { "Web login title overlaps the status bar: ${bar.top} < $statusBar" }
    capture(folder, "web-login-${palette.lowercase()}")
    click("返回")
    frames(host)
    check(find("授权信息", exact = true) != null) { "Web login did not return to the editor" }
    check(find("保存", exact = true, clickable = true)?.isEnabled == true)
    check(find("粘贴 Cred", exact = true) != null && find("显示 Cred", exact = true) != null) { "Cred field buttons missing" }
    editorMessages.clear()
    click("保存")
    frames(host)
    // The name is optional, so an empty form only reports the missing Cred, as a toast rather than inline text.
    check(editorMessages.toList() == listOf("请填写有效的 Cred。")) { "Editor messages: $editorMessages" }
    check(find("请填写有效的 Cred") == null) { "Local validation is still shown inline" }
    verifyAccountKeyboard(host, folder, "account-${palette.lowercase()}")
}

private fun AppUiVerification.verifyAppearanceSettings(host: MainActivity, palette: String) {
    click("返回")
    frames(host)
    check(dialog.value.isEmpty())
    scrollToTag(host, "settings-appearance", 2)
    frames(host)
    click("深色模式")
    frames(host)
    click("跟随系统")
    frames(host)
    check(theme.value == "SYSTEM")
    click("深色模式")
    frames(host)
    click(if (palette == "LIGHT") "关闭" else "开启")
    frames(host)
    check(theme.value == palette)
    check(find("底栏模糊", exact = true) == null && find("模糊强度", exact = true) == null)
    check(page.intValue == 2)
}

private fun AppUiVerification.largeFontCases(host: MainActivity, folder: File, scale: Float): Int {
    var cases = 0
    runOnMainSync {
        fontScale.value = scale
        longNames.value = true
        page.intValue = 0
    }
    frames(host)
    for (palette in listOf("LIGHT", "DARK")) {
        runOnMainSync {
            theme.value = palette
            page.intValue = 0
        }
        frames(host)
        for ((label, destination) in listOf("部件" to 1, "我的" to 2, "概览" to 0)) {
            click(label)
            framesUntil(host) { page.intValue == destination }
            check(page.intValue == destination) { "Large-font navigation failed: $label" }
            val bounds = Rect().apply { checkNotNull(find(label, exact = true)).getBoundsInScreen(this) }
            check(!bounds.isEmpty && bounds.top >= 0 && bounds.bottom <= host.window.decorView.height)
        }
        for (target in 0..2) {
            runOnMainSync { page.intValue = target }
            frames(host)
            runOnMainSync {
                val list = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:$target" }
                checkNotNull(list.config.getOrNull(SemanticsActions.ScrollToIndex)?.action).invoke(0)
            }
            frames(host)
            runOnMainSync { verifyPageText(host, target, scale) }
            if (target == 0) verifyOverviewSpacing(host)
            capture(folder, "font-$scale-${palette.lowercase()}-page-$target")
            cases++
        }
    }
    return cases + verifyTrainingCards(host, folder)
}

// Runs on the main thread: text inside the page list may only overflow vertically when it ends in an ellipsis.
private fun AppUiVerification.verifyPageText(host: Activity, target: Int, scale: Float) {
    val list = composeNodes(host).single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:$target" }
    for (node in composeNodes(host)) {
        var ancestor: SemanticsNode? = node
        var inContent = false
        while (ancestor != null) {
            if (ancestor.config.getOrNull(SemanticsProperties.TestTag) == "page-list:$target") inContent = true
            ancestor = ancestor.parent
        }
        if (!inContent) continue
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        for (layout in layouts) check(!layout.didOverflowHeight ||
            (layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1))) {
            "Unexpected vertical clipping at font scale $scale: ${layout.layoutInput.text}"
        }
    }
    if (target != 0) return
    val nodes = composeNodes(host)
    val title = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "section-title:realtime" }
    val card = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "metric-card:理智" }
    check(kotlin.math.abs(title.boundsInRoot.bottom - card.boundsInRoot.top) <= 1) {
        "Extra list gap between section-title:realtime and its card"
    }
    val chip = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "game-chip:arknights" }
    check(chip.boundsInRoot.width < list.boundsInRoot.width) { "Game capsule unexpectedly fills the list" }
    check(chip.config.getOrNull(SemanticsProperties.Selected) == true)
}

private fun AppUiVerification.emptyStateCases(host: MainActivity, folder: File): Int {
    var cases = 0
    runOnMainSync {
        fontScale.value = 1f
        emptyState.value = true
        theme.value = "LIGHT"
        catalog.intValue = 0
    }
    for ((target, prompt, action) in listOf(Triple(0, "还没有游戏角色", "添加森空岛账号"),
        Triple(1, "还没有添加小部件", "浏览全部小部件"), Triple(2, "还没有森空岛账号", "添加账号"))) {
        runOnMainSync { page.intValue = target }
        frames(host)
        for (text in listOf(prompt, action)) {
            val bounds = Rect().apply { requireNotNull(find(text, exact = true)).getBoundsInScreen(this) }
            check(kotlin.math.abs(bounds.centerX() - host.window.decorView.width / 2) <= 2) { "Empty state is not centered: $text" }
        }
        capture(folder, "empty-page-$target")
        cases++
    }
    runOnMainSync {
        page.intValue = 1
        dialog.value = "widget-new"
    }
    frames(host)
    val emptyPrompt = Rect().apply { requireNotNull(find("请先添加森空岛账号", exact = true)).getBoundsInScreen(this) }
    check(kotlin.math.abs(emptyPrompt.centerX() - host.window.decorView.width / 2) <= 2)
    capture(folder, "empty-config")
    return cases + 1
}
