package cafe.miafetta.tsuki

import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.ui.shell
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.widget.StatusWidgetProvider
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

// Opt-in: edits an existing desktop widget's palette, then its role when a second role exists, and saves each change the way
// a user would. Every change is reverted through the editor and verified; a finally block restores the original config if a
// step fails midway. Changing the role refetches that role's data, so the widget briefly shows a loading state afterwards.
internal fun widgetEditorChangeVerification(runner: WidgetRenderInstrumentation, host: MainActivity): String {
    val id = StatusWidgetProvider.owned(runner.targetContext).firstOrNull { readConfig(runner, it) != null }
        ?: error("Requires an existing configured widget; this test never creates one")
    val original = checkNotNull(readConfig(runner, id))
    val results = mutableListOf<String>()
    var roleCount = "?"
    try {
        val (fromTheme, toTheme) = if (original.theme.name == "DARK") "深色" to "浅色" else "浅色" to "深色"
        saveChange(runner, host, id, "配色", toTheme)
        val palette = checkNotNull(readConfig(runner, id))
        check(palette.theme != original.theme && palette.sameDataBinding(original)) { "Palette save stored $palette" }
        saveChange(runner, host, id, "配色", fromTheme)
        check(readConfig(runner, id) == original) { "Palette was not restored: ${readConfig(runner, id)}" }
        results += "palette ${original.theme}→${palette.theme}→${original.theme}"

        val roles = roleChoice(runner, host, id)
        roleCount = if (roles == null) "1" else "2+"
        if (roles == null) results += "role change skipped (only one role)"
        else {
            val (current, other) = roles
            saveChange(runner, host, id, "角色", other)
            val role = checkNotNull(readConfig(runner, id))
            check(role.characterId != original.characterId || role.accountId != original.accountId) { "Role save stored $role" }
            check(role.theme == original.theme) { "Role save changed the palette" }
            saveChange(runner, host, id, "角色", current)
            check(readConfig(runner, id) == original) { "Role was not restored: ${readConfig(runner, id)}" }
            results += "role changed and restored"
        }
    } finally {
        if (readConfig(runner, id) != original) restore(runner, original)
    }
    check(readConfig(runner, id) == original) { "Original configuration was not restored" }
    val changes = results.joinToString("; ")
    return "PASS: started from ${original.theme} with $roleCount role(s); edited saves return RESULT_OK and store the new value; " +
        "$changes; original config restored"
}

// Opens the editor, picks `value` from the `row` dropdown and saves; the editor must return RESULT_OK with the widget id.
private fun saveChange(runner: WidgetRenderInstrumentation, host: MainActivity, id: Int, row: String, value: String) {
    val result = editorResult(runner, host, id) {
        clickLabel(runner, row)
        clickLabel(runner, value)
        clickLabel(runner, "保存")
    }
    check(result.resultCode == Activity.RESULT_OK) { "Saving $row=$value returned ${result.resultCode}" }
    check(result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) == id) { "Save did not return the widget id" }
}

// Reads the role menu without saving. The menu is the window that appears after tapping the row; the selected role is the
// menu item that the row itself also shows. Returns (selected, another) or null when there is only one role.
private fun roleChoice(runner: WidgetRenderInstrumentation, host: MainActivity, id: Int): Pair<String, String>? {
    var choice: Pair<String, String>? = null
    val result = editorResult(runner, host, id) {
        Thread.sleep(1200)
        val before = windowTexts(runner)
        clickLabel(runner, "角色")
        Thread.sleep(600)
        val menu = windowTexts(runner).filterKeys { it !in before.keys }.values.flatten().distinct()
        val shown = before.values.flatten().toSet()
        val selected = menu.firstOrNull { it in shown }
        val other = menu.firstOrNull { it != selected }
        if (selected != null && other != null) choice = selected to other
        runner.uiAutomation.shell("input keyevent BACK")
        Thread.sleep(600)
        clickLabel(runner, "关闭")
    }
    check(result.resultCode == Activity.RESULT_CANCELED) { "Reading the role menu returned ${result.resultCode}" }
    return choice
}

private fun windowTexts(runner: WidgetRenderInstrumentation): Map<Int, List<String>> {
    runner.uiAutomation.clearCache()
    fun visit(node: AccessibilityNodeInfo?, into: MutableList<String>) {
        if (node == null || !node.isVisibleToUser) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(into::add)
        for (index in 0 until node.childCount) visit(node.getChild(index), into)
    }
    return runner.uiAutomation.windows.associate { window -> window.id to mutableListOf<String>().also { visit(window.root, it) } }
}

// Safety net: writes the original binding back through the same IPC the editor uses.
private fun restore(runner: WidgetRenderInstrumentation, config: WidgetInstanceConfig) {
    runner.targetContext.storageCall(Storage.CONFIGURE, Bundle().apply {
        putInt(Storage.WIDGET_ID, config.widgetId)
        putString(Storage.ACCOUNT_ID, config.accountId)
        putString(Storage.CHARACTER_ID, config.characterId)
        putString(Storage.PROVIDER_ID, config.providerId)
        putString(Storage.FEATURE_ID, config.featureId)
        putString(Storage.THEME, config.theme.name)
        putBoolean(Storage.SIMULATE_FAILURE, config.simulateFailure)
    })
}
