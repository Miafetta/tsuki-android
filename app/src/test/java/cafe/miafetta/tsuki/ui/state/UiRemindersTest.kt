package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

// How the widget process's reminder state (widget/ReminderAccess.overview) reads on the settings page.
class UiRemindersTest {
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "a", "1~000000011")

    private fun rule(subject: ReminderSubject, metricId: String, mode: String, threshold: Int? = null, enabled: Boolean? = true) =
        """{"providerId":"${subject.providerId}","accountId":"${subject.accountId}","characterId":"${subject.characterId}",""" +
            """"metricId":"$metricId","mode":"$mode"""" + (threshold?.let { ""","threshold":$it""" } ?: "") +
            (enabled?.let { ""","enabled":$it""" } ?: "") + "}"

    @Test fun readsTheSwitchAndEveryRule() {
        val json = """{"enabled":true,"rules":[${rule(arknights, "arknights.sanity", "PERCENT", 80)},""" +
            "${rule(arknights, "arknights.training", "EVENT")},${rule(endfield, "endfield.sanity", "VALUE", 200, false)}]}"
        val state = UiReminders.parse(json)
        assertTrue(state.enabled)
        assertEquals(listOf(ReminderRule(arknights, "arknights.sanity", ReminderMode.PERCENT, 80),
            ReminderRule(arknights, "arknights.training", ReminderMode.EVENT),
            ReminderRule(endfield, "endfield.sanity", ReminderMode.VALUE, 200, enabled = false)), state.rules)
        // A missing threshold (FULL, EVENT) reads as none.
        assertNull(state.rule(arknights, "arknights.training")!!.threshold)
        assertNull(state.rule(endfield, "arknights.sanity"))
    }

    @Test fun anEmptyOrMissingStateIsOffWithoutRules() {
        assertEquals(UiReminders(), UiReminders.parse("{}"))
        assertEquals(UiReminders(), UiReminders.parse("""{"enabled":false,"rules":[]}"""))
        assertThrows(JSONException::class.java) { UiReminders.parse("not json") }
    }

    @Test fun rulesThisVersionCannotShowAreSkipped() {
        val json = """{"enabled":true,"rules":[${rule(arknights, "arknights.sanity", "SOMEDAY")},""" +
            "${rule(arknights, "arknights.tower", "FULL")},${rule(arknights, "arknights.training", "FULL")}," +
            "${rule(arknights, "arknights.sanity", "PERCENT", 500)},${rule(arknights, "arknights.sanity", "PERCENT")}," +
            """{"metricId":"arknights.sanity","mode":"FULL"},"oops",""" +
            "${rule(endfield, "endfield.sanity", "FULL", enabled = null)}]}"
        // Unknown mode, metric without reminders, mode the metric does not offer, threshold out of range, missing
        // threshold, missing role and a non-object entry are skipped; the rest still reads.
        assertEquals(listOf(ReminderRule(endfield, "endfield.sanity", ReminderMode.FULL)), UiReminders.parse(json).rules)
    }

    @Test fun localChangesReplaceOrRemoveOneRule() {
        val state = UiReminders(true, listOf(ReminderRule(arknights, "arknights.sanity", ReminderMode.FULL),
            ReminderRule(endfield, "endfield.sanity", ReminderMode.FULL)))
        val changed = state.with(ReminderRule(arknights, "arknights.sanity", ReminderMode.PERCENT, 90))
        assertEquals(2, changed.rules.size)
        assertEquals(90, changed.rule(arknights, "arknights.sanity")!!.threshold)
        assertEquals(listOf(ReminderRule(endfield, "endfield.sanity", ReminderMode.FULL)),
            state.without(arknights, "arknights.sanity").rules)
    }
}
