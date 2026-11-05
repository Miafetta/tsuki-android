package cafe.miafetta.tsuki.widget

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.Future

class WidgetRefreshJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private val images = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val tasks = mutableMapOf<Int, Future<*>>()
    private val imageTasks = mutableMapOf<Int, Future<*>>()
    private val active = mutableMapOf<Int, JobParameters>()

    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.extras.getInt("widgetId", -1)
        if (id != params.jobId || id !in StatusWidgetProvider.owned(this)) return false
        imagePhase.remove(id)
        active[id] = params
        tasks[id] = executor.submit {
            var repeat = false
            try { repeat = WidgetRuntime.get(this).runRefresh(id) }
            finally {
                main.post {
                    if (active[id] === params) {
                        tasks.remove(id)
                        if (repeat) finish(params, true)
                        else {
                            imagePhase.add(id)
                            imageTasks[id] = images.submit {
                                try { WidgetRuntime.get(this).refreshAvatars(id) }
                                catch (_: Exception) { /* Images keep placeholders; the game snapshot already rendered. */ }
                                finally { main.post { if (active[id] === params) finish(params) } }
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val imagesOnly = downloadingAvatars(params.jobId)
        if (active[params.jobId] === params) {
            imagePhase.remove(params.jobId)
            active.remove(params.jobId)
            WidgetRuntime.get(this).stopRefresh(params.jobId)
            tasks.remove(params.jobId)?.cancel(true)
            imageTasks.remove(params.jobId)?.cancel(true)
        }
        return !imagesOnly && params.jobId in StatusWidgetProvider.owned(this)
    }

    private fun finish(params: JobParameters, repeat: Boolean = false) {
        imagePhase.remove(params.jobId)
        active.remove(params.jobId)
        imageTasks.remove(params.jobId)
        jobFinished(params, false)
        // A role may have changed during the independent image phase.
        val runtime = WidgetRuntime.get(this)
        val waiting = runCatching { runtime.read(params.jobId)?.state == cafe.miafetta.tsuki.core.RefreshState.LOADING }
            .getOrDefault(false)
        if (repeat || waiting) runtime.update(params.jobId, true)
    }

    override fun onDestroy() {
        imagePhase.removeAll(active.keys)
        // A completed download may post after destruction; it must not start work on stopped executors.
        active.clear()
        main.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        images.shutdownNow()
        tasks.clear()
        imageTasks.clear()
        super.onDestroy()
    }

    companion object {
        private val imagePhase = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        internal fun downloadingAvatars(id: Int) = id in imagePhase
    }
}
