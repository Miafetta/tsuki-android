package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.core.ReminderChecks
import cafe.miafetta.tsuki.core.ReminderObservations
import cafe.miafetta.tsuki.core.ReminderRoleInput
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.reminder.ReminderNotice
import cafe.miafetta.tsuki.core.reminder.ReminderRule
import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.isArknightsSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONException

// Reminder checking in the :widgetProvider process: calibration on every new snapshot, the reminder job's run, and
// rescheduling. Judging and writing records hold the runtime lock, like every reminder operation
// (WidgetRuntimeReminders.kt); posting notices happens after releasing it.
//
// Lock order: an adapter reports a new snapshot (onSnapshot) inside its request, holding the account repository's lock,
// while account changes take the runtime lock first and the repository's second. Taking the runtime lock in onSnapshot
// could therefore deadlock, so the snapshot is only handed to the reminder thread (ReminderServices.judge), which takes
// the runtime lock and then only leaf locks (the reminder, account and role stores).

// Called from both adapters' onSnapshot, after the role cache was written. Never blocks.
internal fun WidgetRuntime.calibrateReminders(providerId: String, accountId: String, characterId: String,
    snapshot: WidgetSnapshot) {
    reminderServices.judge.submit { judgeSnapshot(providerId, accountId, characterId, snapshot) }
}

private fun WidgetRuntime.judgeSnapshot(providerId: String, accountId: String, characterId: String, snapshot: WidgetSnapshot) {
    val subject = try {
        ReminderSubject(providerId, accountId, characterId)
    } catch (_: IllegalArgumentException) {
        return
    }
    val posts = synchronized(this) {
        val notices = reminderStep {
            calibrate(subject, snapshot.fetchedAt, { observation(it, snapshot, fresh = true) }, System.currentTimeMillis())
        } ?: return
        posts(notices)
    }
    reminderServices.notifier.post(posts)
    rescheduleReminders()
}

internal fun WidgetRuntime.reminderJobStarted() {
    synchronized(this) { reminderServices.jobsRunning++ }
}

internal fun WidgetRuntime.reminderJobEnded() {
    synchronized(this) { reminderServices.jobsRunning-- }
}

// One run of ReminderJob, on the job's thread. Requests go out one role at a time outside every lock, through the
// same fetchers as an overview refresh (the adapter's 20-second window and rate limit apply); their new snapshots reach
// the reminder thread through onSnapshot. The run then finishes on that thread, after those snapshots, and returns
// once every notice of the run has been posted.
internal fun WidgetRuntime.checkReminders() {
    val due = synchronized(this) {
        reminderStep { due(reminderRoles(), reminderServices.notifier.delivery(), System.currentTimeMillis()) }
    }.orEmpty()
    val requests = mutableMapOf<ReminderSubject, Long>()
    val fetched = mutableSetOf<ReminderSubject>()
    for (subject in due) {
        if (Thread.currentThread().isInterrupted) break
        val fetch = roleFetchers[subject.providerId] ?: continue
        val at = System.currentTimeMillis()
        if (synchronized(this) { reminderStep { requesting(subject, at) } } != true) continue
        requests[subject] = at
        try {
            fetch(subject.accountId, subject.characterId)
            fetched += subject
        } catch (_: Exception) {
            // Any failure is "no new data", as a widget refresh records it.
        }
    }
    val finish = reminderServices.judge.submit { finishReminderCheck(requests, fetched) }
    try {
        finish.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (_: Exception) {
        // A stopped or failed finish leaves the rest to the next run, which plans from the reminder file again.
    }
}

private fun WidgetRuntime.finishReminderCheck(requests: Map<ReminderSubject, Long>, fetched: Set<ReminderSubject>) {
    val posts = synchronized(this) {
        val notices = reminderStep {
            finish(reminderRoles(), reminderServices.notifier.delivery(), requests, fetched, System.currentTimeMillis())
        } ?: return
        posts(notices)
    }
    reminderServices.notifier.post(posts)
}

// Schedules the reminder job for the earliest next check, or cancels it when nothing is to be checked: reminders off,
// no enabled rule, notifications off, or a reminder file that cannot be read. Called after every change that moves a
// check: a calibration, a rule saved or deleted, the master switch, an account change, and the end of each run.
internal fun WidgetRuntime.rescheduleReminders() = synchronized(this) {
    if (reminderServices.jobsRunning > 0) return@synchronized
    val now = System.currentTimeMillis()
    val at = reminderStep { wakeAt(reminderRoles(), reminderServices.notifier.delivery(), now) }
    if (at == null) reminderServices.cancel() else reminderServices.schedule(at, now)
}

// Every role of the stored accounts, with its account's rate limit and its cached data, read only when a rule asks.
private fun WidgetRuntime.reminderRoles(): Map<ReminderSubject, ReminderRoleInput> =
    accounts.list().flatMap { account -> account.roles.map { ReminderAccess.subject(account.id, it) to account.retryAt } }
        .associate { (subject, retryAt) ->
            val cached = lazy { cachedSnapshot(subject) }
            subject to ReminderRoleInput(retryAt) { rule -> cached.value?.let { observation(rule, it, fresh = false) } }
        }

// The role's latest successful snapshot; an unreadable cache is no cache.
private fun WidgetRuntime.cachedSnapshot(subject: ReminderSubject): WidgetSnapshot? = try {
    roleData.read(subject.providerId, subject.accountId, subject.characterId).snapshot
} catch (_: IOException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: JSONException) {
    null
}

private fun observation(rule: ReminderRule, snapshot: WidgetSnapshot, fresh: Boolean) =
    ReminderObservations.from(rule.subject, rule.metricId, snapshot, fresh, isArknightsSource(rule.subject.providerId))

// The notifications of [notices], titled with the role names of the stored accounts; a role no longer stored gets none.
private fun WidgetRuntime.posts(notices: List<ReminderNotice>): List<ReminderPost> {
    if (notices.isEmpty()) return emptyList()
    val roles = accounts.list().flatMap { account -> account.roles.map { ReminderAccess.subject(account.id, it) to it } }.toMap()
    return notices.mapNotNull { notice -> roles[notice.subject]?.let { ReminderNotifications.post(notice, it) } }
}

// A reminder file that cannot be read, or a write it refuses, leaves the step undone (null); it never fails the refresh
// or the job that led to it.
private inline fun <T> WidgetRuntime.reminderStep(step: ReminderChecks.() -> T): T? = try {
    ReminderChecks(reminders).step()
} catch (_: IOException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

// Judging takes milliseconds; the bound only keeps a stuck run from holding the job.
private const val FINISH_TIMEOUT_SECONDS = 60L
