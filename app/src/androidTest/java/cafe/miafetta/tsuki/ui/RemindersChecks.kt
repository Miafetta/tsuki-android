package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.state.ReminderOptions
import cafe.miafetta.tsuki.ui.state.ReminderRoleOption
import cafe.miafetta.tsuki.ui.theme.Branding

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import cafe.miafetta.tsuki.core.reminder.ReminderCapability
import cafe.miafetta.tsuki.providers.SklandGame

// What the two reminder pages show, read from their semantics nodes; call on the main thread. Checks use test tags,
// semantic roles, the group titles of ReminderRoles and the texts of ReminderOptions, ReminderAccounts and
// ThresholdSlider, never notification text.
internal object RemindersChecks {
    const val PAGE = "reminders-page"
    const val ACCOUNT_PAGE = "reminder-account-page"
    const val ACCOUNTS = "reminder-accounts"
    const val ACCOUNT_ROW = "reminder-account:"
    const val ROLE = "reminder-role:"
    const val ROW = "reminder-row:"
    const val EMPTY = "reminder-empty"
    const val THRESHOLD = "reminder-threshold"
    const val BACKGROUND = "reminder-background"
    const val STATUS = "reminder-status"

    // The metric rows each game offers today; a change to the registry or the row order has to update this on purpose.
    fun rows(game: SklandGame) = when (game) {
        SklandGame.ARKNIGHTS -> 7
        SklandGame.ENDFIELD -> 4
    }

    // "通知提醒": the master switch is the page's only switch and shows the fixture's state (off); no status group while
    // off, no role group; the account's row, which opens its page, alone in the accounts card under the platform's
    // account title, with its name and [summary]; then 后台运行, whose card holds only 应用信息, with the note right
    // after that card, outside it, and nothing after the note.
    fun main(page: SemanticsNode, accountId: String, name: String, summary: String) {
        val switches = page.outermost(setOf(Role.Switch))
        check(switches.size == 1) { "Expected the master switch alone, found ${switches.size} switches" }
        check(toggle(switches.single()) == ToggleableState.Off) { "Master switch is not off, the fixture has reminders off" }
        val nodes = page.descendants()
        check(nodes.none { it.tag == STATUS }) { "Status group shown while reminders are off" }
        check(nodes.none { it.tag == EMPTY }) { "The card for no account is shown while an account is listed" }
        check(nodes.none { it.tag?.startsWith(ROLE) == true }) { "Role groups shown on the main page" }
        check("${Branding.PLATFORM}账号" in page.texts()) { "Accounts group title missing" }
        val accounts = checkNotNull(nodes.firstOrNull { it.tag == ACCOUNTS }) { "Accounts card missing" }
        val rows = accounts.descendants().filter { it.tag?.startsWith(ACCOUNT_ROW) == true }
        check(rows.map { it.tag } == listOf(ACCOUNT_ROW + accountId)) { "Account rows ${rows.map { it.tag }}" }
        check(rows.single().texts() == listOf(name, summary)) { "Account row ${rows.single().texts()}" }
        check(rows.single().config.getOrNull(SemanticsActions.OnClick) != null) { "The account row does not open its page" }
        val order = listOf(ACCOUNTS, BACKGROUND).map { tag -> nodes.indexOfFirst { it.tag == tag } }
        check(order.none { it < 0 } && order == order.sorted()) { "Page order is $order" }
        check("后台运行" in page.texts()) { "后台运行 group title missing" }
        val background = nodes.first { it.tag == BACKGROUND }
        check(background.texts() == listOf("应用信息")) { "后台运行 card holds ${background.texts()}" }
        val siblings = checkNotNull(background.parent) { "后台运行 card has no parent" }.children
        val after = siblings.drop(siblings.indexOfFirst { it.id == background.id } + 1)
        check(after.size == 1 && !after.single().text.isNullOrBlank()) { "Below the 后台运行 card: ${after.map { it.text }}" }
    }

    // An account's page titled [name], with one card per role in [options] order (ReminderAccounts.options), each
    // titled with its label and holding one row per metric in ReminderOptions.rows order: a switch for the two metrics
    // with a single mode, else a condition menu. With no rule every switch is off, every menu shows "关闭" and no
    // threshold row or slider has unfolded.
    fun accountPage(page: SemanticsNode, name: String, options: List<ReminderRoleOption>) {
        check(page.config.getOrNull(SemanticsProperties.PaneTitle) == name) { "Account page is not titled $name" }
        val cards = page.descendants().filter { it.tag?.startsWith(ROLE) == true }
        check(cards.map { it.tag } == options.map { ROLE + it.key }) { "Role cards ${cards.map { it.tag }}" }
        options.zip(cards).forEach { (option, card) ->
            check(option.label in page.texts()) { "Group title ${option.label} missing" }
            val expected = ReminderOptions.rows(option.game)
            check(expected.size == rows(option.game)) { "${option.game} lists ${expected.size} rows" }
            val found = card.descendants().filter { it.tag?.startsWith(ROW) == true }
            check(found.map { it.tag } == expected.map { ROW + it.metricId }) { "${option.label}: rows ${found.map { it.tag }}" }
            found.zip(expected).forEach { (row, capability) -> checkOff(row, capability) }
        }
        check(page.descendants().none { it.tag == THRESHOLD }) { "A threshold row is shown with no rule" }
        check(page.descendants().none { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }) {
            "A slider is shown with no rule"
        }
    }

    // The sample's one enabled rule: the menu of [capability] in [option]'s card shows [condition], and right under the
    // menu, in the same row, the slider with [labels] under it (the value, then the end of the track), without the
    // "阈值" title and without "−" or "+". Nothing else has unfolded, and every other menu still shows "关闭".
    fun sampleRule(page: SemanticsNode, option: ReminderRoleOption, capability: ReminderCapability, condition: String,
        labels: List<String>) {
        val card = checkNotNull(page.descendants().firstOrNull { it.tag == ROLE + option.key }) { "${option.label}: card missing" }
        val row = checkNotNull(card.descendants().firstOrNull { it.tag == ROW + capability.metricId }) { "Sample row missing" }
        check(menu(row) == listOf(ReminderOptions.title(capability), condition)) { "Sample menu ${menu(row)}" }
        val thresholds = page.descendants().filter { it.tag == THRESHOLD }
        val inRow = row.descendants()
        check(thresholds.size == 1 && inRow.any { it.id == thresholds.single().id }) { "Threshold rows: ${thresholds.size}" }
        val threshold = thresholds.single()
        val menuAt = inRow.indexOfFirst { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
        check(menuAt in 0 until inRow.indexOfFirst { it.id == threshold.id }) { "The slider is not under the menu" }
        check(threshold.descendants().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }) {
            "Threshold row has no slider"
        }
        check(threshold.texts() == labels) { "Under the slider: ${threshold.texts()}" }
        check(ReminderOptions.THRESHOLD !in page.texts()) { "A 阈值 title is shown beside the slider" }
        val steps = page.descendants().filter { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                .any { it == ReminderOptions.DECREASE || it == ReminderOptions.INCREASE }
        }
        check(steps.isEmpty()) { "Step buttons shown beside a slider" }
        val others = page.descendants().filter { it.tag?.startsWith(ROW) == true && it.id != row.id }
        val menus = others.filter { other -> other.outermost(setOf(Role.DropdownList)).isNotEmpty() }.map { menu(it).last() }
        check(menus.isNotEmpty() && menus.all { it == ReminderOptions.OFF }) { "Other menus show $menus" }
    }

    // A metric row with no rule: a switch that is off, or a menu titled with the metric that shows "关闭".
    private fun checkOff(row: SemanticsNode, capability: ReminderCapability) {
        val title = ReminderOptions.title(capability)
        if (ReminderOptions.isSwitch(capability)) {
            check(title in row.texts()) { "Switch $title missing" }
            check(toggle(row) == ToggleableState.Off) { "$title is not off" }
            check(row.outermost(setOf(Role.DropdownList)).isEmpty()) { "$title has a menu" }
        } else {
            check(menu(row) == listOf(title, ReminderOptions.OFF)) { "$title menu ${menu(row)}" }
            check(toggle(row) == null) { "$title has a switch" }
        }
    }

    // The texts of the one condition menu in [row]: its title, then the value it shows.
    private fun menu(row: SemanticsNode): List<String> {
        val menus = row.outermost(setOf(Role.DropdownList))
        check(menus.size == 1) { "Expected one condition menu, found ${menus.size}" }
        return menus.single().texts()
    }

    private fun toggle(node: SemanticsNode) =
        (listOf(node) + node.descendants()).firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.ToggleableState) }
}
