package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.ui.RemindersChecks.ACCOUNT_PAGE
import cafe.miafetta.tsuki.ui.RemindersChecks.ACCOUNT_ROW
import cafe.miafetta.tsuki.ui.RemindersChecks.PAGE
import cafe.miafetta.tsuki.ui.components.accountTitle
import cafe.miafetta.tsuki.ui.state.AppState
import cafe.miafetta.tsuki.ui.state.ReminderAccounts
import cafe.miafetta.tsuki.ui.state.ReminderOptions
import cafe.miafetta.tsuki.ui.state.ReminderRoleOption
import cafe.miafetta.tsuki.ui.state.ThresholdSlider
import cafe.miafetta.tsuki.ui.state.UiAccount
import cafe.miafetta.tsuki.ui.state.UiNotifier
import cafe.miafetta.tsuki.ui.state.UiPreferences
import cafe.miafetta.tsuki.ui.state.UiReminders
import cafe.miafetta.tsuki.ui.theme.Branding

import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsNode
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cafe.miafetta.tsuki.MainActivity
import cafe.miafetta.tsuki.clickLabel
import cafe.miafetta.tsuki.core.reminder.ReminderCapabilities
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject

// Opens "我的" → "通知提醒" → the fixture's account in the real MainActivity content over an in-memory AppState, so no
// call reaches the widget process. The fixture answers every reminder write with an error and counts it: the scenario
// never saves a rule and never touches the master switch, which is also what would ask for the notification permission,
// so it requests no permission and nothing can post a notification. Both pages are opened twice: first with no rule,
// then, after the fixture starts answering with one enabled rule held in memory, to see that rule's menu and slider.
// Each time both pages are left with "返回", one level at a time. What the pages show is checked in RemindersChecks.
internal class RemindersVerification(private val runner: WidgetRenderInstrumentation, host: MainActivity) {
    private val writes = CopyOnWriteArrayList<String>()
    private val fixture = AppState(host.applicationContext, UiNotifier {}) { method, _ -> answer(method) }
    private var current = host
    // Whether the fixture answers with REMINDERS_SAMPLE; read on the IO threads that run the fixture's calls.
    @Volatile private var sample = false

    fun run(): String {
        val preferences = current.getSharedPreferences(UiPreferences.FILE, 0)
        // Selecting fixture roles writes the selected-role preferences; the user's choices go back afterwards.
        val saved = preferenceKeys.associateWith { preferences.getString(it, null) }
        try {
            val host = current
            runner.runOnMainSync {
                host.viewModelStore.clear()
                ViewModelProvider(host, viewModelFactory { initializer { fixture } })[AppState::class.java]
            }
            recreate()
            waitFor("Fixture roles did not load") { fixture.roles.size == 2 }
            val account = onMain { fixture.accounts.single() }
            val options = ReminderAccounts.options(account)
            check(options.map { it.game } == SklandGame.entries) { "Fixture should list one role per game, in game order" }
            openReminders(account, ReminderAccounts.NONE_ON)
            openAccount(account)
            onMain { RemindersChecks.accountPage(page(ACCOUNT_PAGE), accountTitle(account), options) }
            leaveAccount()
            leaveReminders()
            sample = true
            val summary = ReminderAccounts.summary(account, UiReminders.parse(REMINDERS_SAMPLE))
            openReminders(account, summary)
            openAccount(account)
            val unfolded = checkSample(options.first())
            leaveAccount()
            leaveReminders()
            check(writes.isEmpty()) { "Reminder writes were attempted: $writes" }
            return "PASS: Me → 通知提醒 opened; master switch off, no status group while off; account row " +
                "${accountTitle(account)} \"${ReminderAccounts.NONE_ON}\" before 后台运行, which is last with its note below " +
                "the card; its page: " + options.joinToString { "${it.label} ${RemindersChecks.rows(it.game)} rows" } +
                ", every menu \"${ReminderOptions.OFF}\", every switch off, no slider; reopened with an enabled rule in " +
                "memory: row \"$summary\", $unfolded; Back returned to 通知提醒, then to Me, both times; no reminder write. " +
                "Skipped (each needs a write or leaves the app): master switch and permission request, status group, " +
                "choosing in a menu, the switches, dragging the slider, the steps of a value without a known limit, " +
                "应用信息, the page without accounts and an account without roles"
        } finally {
            val last = current
            runner.runOnMainSync { last.finish() }
            val deadline = System.currentTimeMillis() + 5000
            while (!last.isDestroyed && System.currentTimeMillis() < deadline) Thread.sleep(50)
            preferences.edit(commit = true) {
                saved.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            }
        }
    }

    // Reads answer from memory; reminder writes are refused and counted. Every other call gets an empty answer.
    private fun answer(method: String) = Bundle().apply {
        when (method) {
            Storage.OVERVIEW -> putString(Storage.JSON, OVERVIEW)
            Storage.REMINDERS -> putString(Storage.JSON, if (sample) REMINDERS_SAMPLE else REMINDERS_OFF)
            in WRITES -> {
                writes += method
                putBoolean(Storage.ERROR, true)
            }
        }
    }

    // Me → 通知提醒, then the main page's checks once the account row shows [summary] (the rules are read as it opens).
    private fun openReminders(account: UiAccount, summary: String) {
        // Back from the page lands on Me, and a selected Tab is not clickable to accessibility services.
        if (onMain { current.selectedNavTab() } != 2) clickLabel(runner, "我的")
        settle()
        reveal({ it.tag == "page-list:2" }) { it.text == ENTRY }
        check(onMain { current.clickSemantics { it.text == ENTRY } }) { "Cannot open $ENTRY" }
        waitFor("Reminder page did not open") { node(PAGE) != null }
        waitFor("The account row does not show \"$summary\"") {
            node(ACCOUNT_ROW + account.id)?.texts() == listOf(accountTitle(account), summary)
        }
        settle()
        onMain { RemindersChecks.main(page(PAGE), account.id, accountTitle(account), summary) }
    }

    // The account's row opens its page; the row is clicked by its tag, since "我的" under the page shows the same name.
    private fun openAccount(account: UiAccount) {
        check(onMain { current.clickSemantics { it.tag == ACCOUNT_ROW + account.id } }) { "Cannot open the account page" }
        waitFor("Account page did not open") { node(ACCOUNT_PAGE) != null }
        settle()
    }

    // The sample rule (Arknights sanity at 80 percent). The fixture has no snapshot, so the limit is unknown: the stored
    // percentage stays listed in the menu, and its slider keeps the range of a percentage.
    private fun checkSample(option: ReminderRoleOption): String {
        val capability = checkNotNull(ReminderCapabilities[SANITY])
        val condition = ReminderOptions.choiceLabel(PERCENT)
        val slider = checkNotNull(ThresholdSlider.of(capability, PERCENT, null))
        val labels = listOf(slider.valueLabel(SAMPLE_PERCENT), slider.endLabel)
        waitFor("The sample rule's slider did not unfold") { node(RemindersChecks.THRESHOLD) != null }
        settle()
        onMain { RemindersChecks.sampleRule(page(ACCOUNT_PAGE), option, capability, condition, labels) }
        return "${capability.name} menu \"$condition\" with a slider under it labelled ${labels.joinToString(" and ")}, " +
            "without 阈值 or step buttons, every other menu \"${ReminderOptions.OFF}\""
    }

    private fun leaveAccount() {
        clickLabel(runner, "返回")
        waitFor("Account page did not close") { node(ACCOUNT_PAGE) == null }
        settle()
        check(onMain { node(PAGE) != null }) { "Back from the account page did not return to 通知提醒" }
    }

    private fun leaveReminders() {
        clickLabel(runner, "返回")
        waitFor("Reminder page did not close") { node(PAGE) == null }
        settle()
        check(onMain { current.selectedNavTab() } == 2) { "Back did not return to Me" }
    }

    private fun reveal(container: (SemanticsNode) -> Boolean = { false }, match: (SemanticsNode) -> Boolean) {
        repeat(16) {
            if (onMain { current.revealStep(container, match) }) return
            runner.waitForIdleSync()
            Thread.sleep(300)
        }
        error("Cannot bring the node into view")
    }

    // Call on the main thread.
    private fun node(tag: String) = current.semanticsNodes().firstOrNull { it.tag == tag }

    // Call on the main thread.
    private fun page(tag: String) = checkNotNull(node(tag)) { "$tag is not shown" }

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

    private companion object {
        const val ENTRY = "通知提醒"
        const val ACCOUNT = "reminder-fixture"
        val WRITES = setOf(Storage.SAVE_REMINDER, Storage.DELETE_REMINDER, Storage.SET_REMINDERS_ENABLED)
        val preferenceKeys = listOf(UiPreferences.ROLE) + Branding.games.map { UiPreferences.gameRole(it.first) }

        fun role(id: String, label: String, provider: String, server: String? = null) = JSONObject().put(Storage.ID, id)
            .put(Storage.LABEL, label).put(Storage.PROVIDER_ID, provider).apply { server?.let { put(Storage.SERVER, it) } }

        // One account with an Arknights role and an Endfield role; no widgets.
        val OVERVIEW = JSONObject().put(Storage.ACCOUNTS, JSONArray().put(JSONObject().put(Storage.ID, ACCOUNT)
            .put(Storage.NAME, "提醒测试账号").put(Storage.ROLES, JSONArray()
                .put(role(ARKNIGHTS_ROLE, "测试博士 · 官服", SklandArknightsAdapter.ID))
                .put(role("1~200000001", "测试管理员", SklandGame.ENDFIELD.providerId, "官服")))))
            .put(Storage.WIDGETS, JSONArray()).toString()

        val REMINDERS_OFF = JSONObject().put(Storage.ENABLED, false).put(Storage.RULES, JSONArray()).toString()

        const val ARKNIGHTS_ROLE = "100000001"
        const val SANITY = "arknights.sanity"
        const val SAMPLE_PERCENT = 80

        // Reminders still off, with one enabled rule for the Arknights role: sanity at 80 percent. Held in memory only.
        val REMINDERS_SAMPLE = JSONObject().put(Storage.ENABLED, false).put(Storage.RULES, JSONArray().put(JSONObject()
            .put(Storage.PROVIDER_ID, SklandArknightsAdapter.ID).put(Storage.ACCOUNT_ID, ACCOUNT)
            .put(Storage.CHARACTER_ID, ARKNIGHTS_ROLE).put(Storage.METRIC_ID, SANITY).put(Storage.MODE, PERCENT.name)
            .put(Storage.THRESHOLD, SAMPLE_PERCENT).put(Storage.ENABLED, true))).toString()
    }
}
