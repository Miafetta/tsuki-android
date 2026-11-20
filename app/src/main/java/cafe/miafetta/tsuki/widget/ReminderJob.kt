package cafe.miafetta.tsuki.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

// The one reminder job (docs/DEVELOPMENT_CONSTRAINTS.md §2). A run checks every role that is due and then schedules the
// job again for the earliest next check of all roles (WidgetRuntimeReminderChecks.kt). Roles never get jobs of their
// own, so no role can take another's job ID.
class ReminderJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var current: Run? = null

    // One run: whether its thread started, and whether it has ended. A run stopped or destroyed before its thread
    // starts never reaches its finally block, so it is ended where it is stopped; ending twice does nothing, so the
    // count of running jobs can neither leak (which would hold back every later reschedule) nor go below zero.
    private class Run(val params: JobParameters) {
        val started = AtomicBoolean(false)
        val ended = AtomicBoolean(false)
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val runtime = WidgetRuntime.get(this)
        runtime.reminderJobStarted()
        val run = Run(params)
        current = run
        task = executor.submit {
            run.started.set(true)
            try { runtime.checkReminders() }
            finally { end(runtime, run) }
        }
        return true
    }

    // The run schedules the next check itself as it ends; asking the system to reschedule as well covers a process
    // that dies before that.
    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel(true)
        endUnstarted()
        return true
    }

    override fun onDestroy() {
        executor.shutdownNow()
        endUnstarted()
        super.onDestroy()
    }

    // A cancelled task that had not started never runs, so its run is ended here; a started one ends in its own thread.
    private fun endUnstarted() {
        val run = current ?: return
        if (!run.started.get()) end(WidgetRuntime.get(this), run)
    }

    private fun end(runtime: WidgetRuntime, run: Run) {
        if (!run.ended.compareAndSet(false, true)) return
        // Finished first, so that no reschedule can stop this run; reschedules asked for until now were deferred and
        // are covered by the one below, which reads the latest state.
        jobFinished(run.params, false)
        runtime.reminderJobEnded()
        // A stopped run was interrupted; scheduling the next check must still go through.
        Thread.interrupted()
        runtime.rescheduleReminders()
    }

    internal companion object {
        // Negative, apart from WidgetRefreshJob, whose job IDs are appWidgetIds and therefore positive.
        const val JOB_ID = -1
        // The time the pending job was scheduled for, so that scheduling the same time again changes nothing.
        const val AT = "at"

        // Persisted across restarts (RECEIVE_BOOT_COMPLETED) and without a network constraint: offline, the request
        // fails quickly and counts as no new data, and rules that may be worked out from known times still report.
        fun info(context: Context, at: Long, now: Long): JobInfo =
            JobInfo.Builder(JOB_ID, ComponentName(context, ReminderJob::class.java))
                .setPersisted(true)
                .setMinimumLatency((at - now).coerceAtLeast(0))
                .setExtras(PersistableBundle().apply { putLong(AT, at) })
                .build()
    }
}
