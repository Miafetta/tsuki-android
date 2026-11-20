package cafe.miafetta.tsuki.widget

import android.app.job.JobScheduler
import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// The Android side of reminders, one per WidgetRuntime: notifications, the thread that judges, and the one reminder job.
internal class ReminderServices(private val context: Context) {
    val notifier = ReminderNotifier(context)

    // Every judgement runs here, one at a time in arrival order: new snapshots handed over from the adapters' request
    // path, and the end of each reminder job (WidgetRuntimeReminderChecks.kt).
    val judge: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "tsuki-reminders") }

    // Reminder job runs in progress, guarded by the runtime lock. Scheduling the job while it runs would stop it, so
    // reschedules wait for the run, which schedules the next check as it ends.
    var jobsRunning = 0

    private val scheduler = context.getSystemService(JobScheduler::class.java)

    // Rescheduling rides along with account changes, settings and refreshes; a scheduler that refuses (its limits on
    // jobs and calls) must not fail them. The next reschedule tries again.
    fun schedule(at: Long, now: Long) = guarded {
        if (scheduler.getPendingJob(ReminderJob.JOB_ID)?.extras?.getLong(ReminderJob.AT, -1) != at) {
            scheduler.schedule(ReminderJob.info(context, at, now))
        }
    }

    fun cancel() = guarded { scheduler.cancel(ReminderJob.JOB_ID) }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            // Refused; nothing else depends on it.
        }
    }
}
