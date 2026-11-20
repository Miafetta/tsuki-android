package cafe.miafetta.tsuki.core.reminder

import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.reminder.ReminderMode.FULL
import cafe.miafetta.tsuki.core.reminder.ReminderMode.PERCENT
import cafe.miafetta.tsuki.core.reminder.ReminderMode.VALUE
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.AWAITING_DATA
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.DISABLED
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOTIFIED
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.NOT_MET
import cafe.miafetta.tsuki.core.reminder.ReminderStatus.STALE
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import org.junit.Assert.*
import org.junit.Test

// Reporting once per crossing: repeated data, re-arming on fresh data only, one record per rule, and starting over.
class ReminderDedupTest : ReminderFixture() {
    private val drones = "arknights.drones"

    @Test fun repeatedDataReportsOnce() {
        val full = rule(drones, FULL)
        val same = observe(drones, 200, 200, fullAt = 0)
        val decisions = full.walk(same to now, same to now + minute, same to now + hour, same.copy(fresh = false) to now + 2 * hour)
        assertStatuses(listOf(NOTIFIED, MET, MET, MET), decisions)
        assertEquals(now, decisions.last().record.firedAt)
    }

    @Test fun freshDataBelowTheThresholdReArmsTheRule() {
        val value = rule(drones, VALUE, 150)
        val decisions = value.walk(observe(drones, 160, 200, dataAt = 1) to now, observe(drones, 40, 200, dataAt = 2) to now + hour,
            observe(drones, 155, 200, dataAt = 3) to now + 2 * hour, observe(drones, 170, 200, dataAt = 4) to now + 3 * hour)
        assertStatuses(listOf(NOTIFIED, NOT_MET, NOTIFIED, MET), decisions)
        assertFalse(decisions[1].record.fired)
        assertEquals(now + 2 * hour, decisions[2].record.firedAt)
        assertEquals(4L, decisions[3].record.dataAt)
    }

    @Test fun workedOutResultsNeverReArmTheRule() {
        val value = rule(drones, VALUE, 150)
        // The last known data without a full time cannot say the value is still met; it must not re-arm either.
        val decisions = value.walk(observe(drones, 160, 200) to now, observe(drones, 40, 200, fresh = false) to now + hour,
            observe(drones, 160, 200) to now + 2 * hour)
        assertStatuses(listOf(NOTIFIED, NOT_MET, MET), decisions)
        assertTrue(decisions[1].record.fired)
        // Modes that are judged on fresh data only are not judged at all without it.
        val trading = "arknights.trading"
        val stale = rule(trading, FULL).walk(observe(trading, 20, 20) to now, observe(trading, 3, 20, fresh = false) to now + hour,
            observe(trading, 20, 20) to now + 2 * hour)
        assertStatuses(listOf(NOTIFIED, AWAITING_DATA, MET), stale)
    }

    @Test fun aRoleHasOneRecordPerRuleWhateverTheWidgets() {
        // Two widgets of the same role (different instances and palettes) map to one subject, so their refreshes judge
        // one rule with one record; the judge has no widget ID to tell them apart.
        val widgets = listOf(7, 8).map { id ->
            WidgetInstanceConfig(id, SklandArknightsAdapter.ID, "skland.arknights.status", "account-a", "000000001")
        }
        val subjects = widgets.map { ReminderSubject(it.providerId, it.accountId, it.characterId) }.distinct()
        assertEquals(listOf(arknights), subjects)
        val full = rule(drones, FULL)
        val fromEachWidget = full.walk(observe(drones, 200, 200) to now, observe(drones, 200, 200) to now + second)
        assertStatuses(listOf(NOTIFIED, MET), fromEachWidget)
        assertEquals(1, fromEachWidget.count { it.notify })
    }

    @Test fun disablingClearsTheRecordAndEnablingStartsOver() {
        val enabled = rule(drones, FULL)
        val first = judge(enabled, observe(drones, 200, 200))
        assertEquals(NOTIFIED, first.status)
        val disabled = judge(enabled.copy(enabled = false), observe(drones, 200, 200), first.record, now + hour)
        assertEquals(ReminderDecision(DISABLED, TriggerRecord()), disabled)
        // Enabled again (also how the master switch turning back on resets records): a rule already met reports once,
        // and nothing that happened while it was off is replayed.
        val again = enabled.walk(observe(drones, 200, 200) to now + 2 * hour, observe(drones, 200, 200) to now + 3 * hour,
            record = disabled.record)
        assertStatuses(listOf(NOTIFIED, MET), again)
        val idle = enabled.walk(observe(drones, 120, 200) to now + 2 * hour, record = TriggerRecord())
        assertStatuses(listOf(NOT_MET), idle)
    }

    @Test fun aChangedThresholdStartsOver() {
        val eighty = rule(drones, PERCENT, 80)
        val fired = judge(eighty, observe(drones, 170, 200)).record
        assertTrue(fired.fired)
        // The same data meets the new, lower threshold: the changed rule reports it as a rule just enabled would.
        assertEquals(NOTIFIED, judge(eighty.copy(threshold = 50), observe(drones, 170, 200), fired).status)
        val higher = judge(eighty.copy(threshold = 90), observe(drones, 170, 200), fired)
        assertEquals(NOT_MET, higher.status)
        assertEquals(TriggerRecord(PERCENT, 90, dataAt = now), higher.record)
        assertEquals(NOTIFIED, judge(rule(drones, VALUE, 170), observe(drones, 170, 200), fired).status)
    }

    @Test fun olderFreshDataIsIgnored() {
        val value = rule(drones, VALUE, 150)
        val fired = judge(value, observe(drones, 160, 200, dataAt = 2_000)).record
        val late = judge(value, observe(drones, 40, 200, dataAt = 1_000), fired, now + minute)
        assertEquals(ReminderDecision(STALE, fired), late)
    }

    @Test fun failedRequestsNeverCountAsFreshData() {
        val value = rule(drones, VALUE, 150)
        val first = judge(value, observe(drones, 100, 200, dataAt = 1_000, fullAt = now + hour))
        assertEquals(1_000L, first.record.dataAt)
        val failed = judge(value, observe(drones, 100, 200, fresh = false, dataAt = 5_000, fullAt = now + hour), first.record,
            now + minute)
        assertEquals(NOT_MET, failed.status)
        assertEquals(first.record, failed.record)
        assertEquals(AWAITING_DATA, judge(value, null, first.record).status)
        assertEquals(first.record, judge(value, null, first.record).record)
    }
}
