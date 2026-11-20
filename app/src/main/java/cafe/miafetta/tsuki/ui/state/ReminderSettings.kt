package cafe.miafetta.tsuki.ui.state

import android.content.Context
import androidx.compose.runtime.*
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.reminder.ReminderCapability
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// State of the reminder settings ("我的" → "通知提醒" and its account pages), held by AppState so it outlives recreation. Every call
// goes through one lock, so answers are applied in the order the calls were made: each answer is the whole state after
// its call, and applying them out of order could bring back an earlier state. A change shows at once; when the last
// pending call has finished the page shows the widget process's answer, or the last one it gave when a call failed.
@Stable
internal class ReminderSettings(private val context: Context, private val calls: ReminderCalls,
    private val messages: UiMessages, private val scope: CoroutineScope,
    private val snapshotOf: suspend (UiRole) -> WidgetSnapshot?) {
    var reminders by mutableStateOf(UiReminders())
        private set
    var system by mutableStateOf(ReminderSystemStatus())
        private set
    private var busy by mutableStateOf(emptySet<String>())
    // The effective limits of the metrics of the roles an account page lists, by role key, then metric ID.
    private var limits by mutableStateOf(emptyMap<String, Map<String, Int?>>())
    // Thresholds shown before they are saved, by metric key: a slider while a finger moves it, or steps waiting for
    // their save, which [waits] holds.
    private var drafts by mutableStateOf(emptyMap<String, Int>())
    private val waits = mutableMapOf<String, Job>()
    // The threshold each metric last had under each condition it left in this session, so choosing that condition again
    // brings it back. Kept in memory only: the stored rule has one condition and one threshold.
    private val previous = mutableMapOf<String, Map<ReminderMode, Int>>()
    private var confirmed = UiReminders()
    private var pending = 0
    private val lock = Mutex()

    val problems get() = ReminderProblem.of(reminders.enabled, system)
    val needsPermission get() = !ReminderSystem.permissionGranted(context)

    fun isBusy(key: String) = key in busy

    // The latest effective limit of the role's metric, or null while unknown or not read yet.
    fun limit(role: UiRole, metricId: String) = limits[role.key]?.get(metricId)

    // The threshold a metric's rows show: the one being chosen, else the enabled rule's (null for a rule without one).
    fun threshold(role: UiRole, metricId: String): Int? =
        drafts[key(role, metricId)] ?: reminders.rule(role.reminderSubject(), metricId)?.takeIf { it.enabled }?.threshold

    // On every return to a reminder page: the user may have changed permissions or channels in the system settings, so the
    // status is read again, and with reminders on the widget process reschedules its checks.
    fun resume() {
        system = ReminderSystem.status(context)
        submit(null, null, READ_FAILED) { calls.read()?.let { if (it.enabled) calls.setEnabled(true) else it } }
    }

    // The system does not tell the app when the permission is granted; calling again lets the widget process
    // reschedule now, whatever the answer was.
    fun permissionAnswered() {
        system = ReminderSystem.status(context)
        if (reminders.enabled) submit(null, null, SAVE_FAILED) { calls.setEnabled(true) }
    }

    // A system settings page this ROM does not have.
    fun reportUnavailable() = messages.send(SETTINGS_UNAVAILABLE)

    fun setEnabled(enabled: Boolean) {
        submit(MASTER, reminders.copy(enabled = enabled), SAVE_FAILED) { calls.setEnabled(enabled) }
    }

    // The switch of a metric with a single mode: on saves that mode, off deletes the rule (choose).
    fun setOn(role: UiRole, capability: ReminderCapability, on: Boolean) =
        choose(role, capability, if (on) capability.modes.single() else null)

    // A metric's condition menu: a condition saves the rule with it at once, null ("关闭") deletes the rule
    // (ReminderOptions.chosen). A threshold still waiting for its save is dropped, and the threshold the rule had is kept
    // for its condition, so choosing that condition again brings it back. While the metric's last change is still being
    // saved its rows keep their look, so a choice made meanwhile is ignored here rather than shown and saved again.
    fun choose(role: UiRole, capability: ReminderCapability, mode: ReminderMode?) {
        val key = key(role, capability.metricId)
        val subject = role.reminderSubject()
        val current = reminders.rule(subject, capability.metricId)?.takeIf { it.enabled }
        if (key in busy || current?.mode == mode) return
        discard(key)
        current?.threshold?.let { previous[key] = previous[key].orEmpty() + (current.mode to it) }
        val kept = mode?.let { previous[key]?.get(it) }
        val rule = ReminderOptions.chosen(subject, capability, mode, kept, limit(role, capability.metricId))
        if (rule != null) save(role, rule) else delete(role, subject, capability.metricId)
    }

    // The slider while a finger moves it: shown only; [release] saves it when the finger lets go.
    fun drag(role: UiRole, capability: ReminderCapability, threshold: Int) {
        val key = key(role, capability.metricId)
        if (key !in busy) drafts = drafts + (key to threshold)
    }

    // A tap of "−" or "+", or a slider change no finger made (accessibility services set the value without ending a
    // drag): shown at once, and saved once no other change has followed for SAVE_DELAY_MILLIS.
    fun nudge(role: UiRole, capability: ReminderCapability, threshold: Int) {
        val key = key(role, capability.metricId)
        if (key in busy) return
        drafts = drafts + (key to threshold)
        waits.remove(key)?.cancel()
        waits[key] = scope.launch {
            delay(SAVE_DELAY_MILLIS)
            waits.remove(key)
            release(role, capability)
        }
    }

    // Saves the threshold being chosen, if any: the slider let go, or the pause after the last step. Nothing is saved
    // when the rule has gone or lost its threshold meanwhile, or when the threshold is the stored one.
    fun release(role: UiRole, capability: ReminderCapability) {
        val key = key(role, capability.metricId)
        val threshold = drafts[key] ?: return
        discard(key)
        val stored = reminders.rule(role.reminderSubject(), capability.metricId)?.takeIf { it.enabled } ?: return
        if (stored.threshold == null || stored.threshold == threshold) return
        save(role, stored.copy(threshold = threshold))
    }

    // Limits come from each listed role's cached snapshot (StorageContract.DASHBOARD, one read per role), never from a
    // request of their own; an account page lists its roles as it opens. Roles are read one after another and each shows
    // as it arrives; until then a role keeps the limits read before, and roles no longer listed are dropped.
    suspend fun loadLimits(roles: List<ReminderRoleOption>) {
        val keys = roles.map { it.key }.toSet()
        limits = limits.filterKeys { it in keys }
        for (option in roles) {
            val snapshot = snapshotOf(option.role)
            val metrics = ReminderOptions.metrics(option.game)
            val read = withContext(Dispatchers.Default) { ReminderOptions.limits(option.role.reminderSubject(), snapshot, metrics) }
            limits = limits + (option.key to read)
        }
    }

    private fun save(role: UiRole, rule: ReminderRule) {
        submit(key(role, rule.metricId), reminders.with(rule), SAVE_FAILED) { calls.save(rule) }
    }

    private fun delete(role: UiRole, subject: ReminderSubject, metricId: String) {
        submit(key(role, metricId), reminders.without(subject, metricId), SAVE_FAILED) { calls.delete(subject, metricId) }
    }

    private fun discard(key: String) {
        waits.remove(key)?.cancel()
        drafts = drafts - key
    }

    // [key] marks the control that waits for the call (null for calls the user did not make), [shown] is the state to
    // show meanwhile.
    private fun submit(key: String?, shown: UiReminders?, failure: String, call: () -> UiReminders?) {
        if (key != null) {
            if (key in busy) return
            busy = busy + key
        }
        pending++
        if (shown != null) reminders = shown
        scope.launch {
            try {
                val result = lock.withLock { withContext(Dispatchers.IO) { call() } }
                if (result != null) confirmed = result else messages.send(failure)
            } finally {
                pending--
                if (key != null) busy = busy - key
                if (pending == 0) reminders = confirmed
            }
        }
    }

    companion object {
        // How long after the last tap of "−" or "+" its threshold is saved: long enough to span a run of taps, so the run
        // is one save rather than one per tap (each save holds off the metric's next change until it returns), and short
        // enough that the save follows soon after the hand stops.
        const val SAVE_DELAY_MILLIS = 400L
        const val MASTER = "master"
        const val SAVE_FAILED = "保存失败"
        const val READ_FAILED = "数据读取失败，请重试"
        const val SETTINGS_UNAVAILABLE = "无法打开系统设置"

        fun key(role: UiRole, metricId: String) = "${role.key}#$metricId"
    }
}
