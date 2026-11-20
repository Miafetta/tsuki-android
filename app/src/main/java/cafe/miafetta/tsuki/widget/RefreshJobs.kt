package cafe.miafetta.tsuki.widget

import android.app.job.JobInfo
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.core.RefreshState
import cafe.miafetta.tsuki.core.WidgetRecord
import java.io.IOException

// The jobs of WidgetRefreshJob: one per widget, with its appWidgetId as job ID. After a rate-limited result the widget
// gets one more refresh once the limit ends (docs/DEVELOPMENT_CONSTRAINTS.md §2); that retry is an ordinary refresh job
// of the widget that waits, so WidgetRuntime.scheduleRefresh treats it like any pending refresh.
internal object RefreshJobs {
    const val WIDGET_ID = "widgetId"
    // Marks the retry, so that a retry limited again schedules nothing: one retry, never a loop.
    const val RETRY = "retry"

    // [delay] is set only for the retry.
    fun info(context: Context, id: Int, delay: Long? = null): JobInfo =
        JobInfo.Builder(id, ComponentName(context, WidgetRefreshJob::class.java))
            .setExtras(PersistableBundle().apply {
                putInt(WIDGET_ID, id)
                putBoolean(RETRY, delay != null)
            })
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(256 * 1024, 1024)
            .apply { if (delay != null) setMinimumLatency(delay) }
            .build()

    // When to retry after [record], the result of a refresh job ([retried]: of the retry itself): at the retry time of
    // a rate limit that is still ahead, unless this was the retry. Null for every other result.
    fun retryAt(record: WidgetRecord?, retried: Boolean, now: Long): Long? = record?.takeIf {
        !retried && it.state == RefreshState.ERROR && it.failure == RefreshFailure.RATE_LIMIT && it.retryAt > now
    }?.retryAt

    // Called as a refresh job ends. A refresh scheduled in the meantime is newer than the result and stays.
    fun retry(runtime: WidgetRuntime, id: Int, retried: Boolean) {
        synchronized(runtime) {
            val now = System.currentTimeMillis()
            val record = try {
                runtime.read(id)
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            val at = retryAt(record, retried, now) ?: return
            if (runtime.scheduler.getPendingJob(id) == null) runtime.scheduler.schedule(info(runtime.application, id, at - now))
        }
    }
}
