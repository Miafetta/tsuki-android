package cafe.miafetta.tsuki.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.view.View
import cafe.miafetta.tsuki.R
import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.providers.AvatarCache
import java.io.File

// No test framework dependency: run with adb shell am instrument -w <test package>/<runner>.
class WidgetRenderInstrumentation : Instrumentation() {
    @Volatile var widgetEditorActivity: cafe.miafetta.tsuki.WidgetConfigActivity? = null
    val uiActivityCreated = java.util.concurrent.CountDownLatch(1)
    @Volatile var uiActivity: cafe.miafetta.tsuki.MainActivity? = null
    var uiContentOnCreate: ((cafe.miafetta.tsuki.MainActivity) -> Unit)? = null
    override fun callActivityOnCreate(activity: Activity, state: Bundle?) {
        super.callActivityOnCreate(activity, state)
        if (activity is cafe.miafetta.tsuki.WidgetConfigActivity) widgetEditorActivity = activity
        if (activity is cafe.miafetta.tsuki.MainActivity) {
            uiActivity = activity
            uiContentOnCreate?.invoke(activity)
            uiActivityCreated.countDown()
        }
    }
    private var appUi = false
    private var redesignV4: String? = null
    private var glassCalibration = false
    private var pageRefinement = false
    private var typography = false
    private var comparison = false
    private var lifecycle = false
    private var argumentsForLiveCheck = false
    private var nativeLive = false
    private var argumentsForSurfaceCheck = false
    private var previewTheme: WidgetTheme? = null
    private var liveResult = ""
    private var hostProbe: String? = null
    private var widgetStartup = false
    private var providerBreakdown = false
    override fun onCreate(arguments: Bundle?) {
        providerBreakdown = arguments?.getString("providerBreakdown") == "true"
        appUi = arguments?.getString("appUi") == "true"
        redesignV4 = arguments?.getString("redesignV4")
        glassCalibration = arguments?.getString("glassCalibration") == "true"
        pageRefinement = arguments?.getString("pageRefinement") == "true"
        typography = arguments?.getString("typography") == "true"
        comparison = arguments?.getString("comparison") == "true"
        lifecycle = arguments?.getString("lifecycle") == "true"
        argumentsForLiveCheck = arguments?.getString("live") == "true"
        nativeLive = arguments?.getString("nativeLive") == "true"
        argumentsForSurfaceCheck = arguments?.getString("surfaceOptions") == "true"
        previewTheme = arguments?.getString("previewTheme")?.let(WidgetTheme::valueOf)
        hostProbe = arguments?.getString("hostProbe")
        widgetStartup = arguments?.getString("widgetStartup") == "true"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val probe = hostProbe
        when {
            widgetStartup -> finishWith(widgetStartupVerification(this))
            providerBreakdown -> finishWith(runCatching { providerReadBreakdown(targetContext) }
                .fold({ true to it }, { false to it.stackTraceToString() }))
            probe != null -> sendHostProbe(probe)
            lifecycle -> finishWith(cafe.miafetta.tsuki.ui.windowLifecycleVerification(this))
            comparison -> try {
                val result = hyperOsComparison(this)
                finish(Activity.RESULT_OK, Bundle().apply { putString("result", result) })
            } catch (failure: Exception) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", failure.stackTraceToString()) })
            }
            appUi || redesignV4 != null || glassCalibration || pageRefinement || typography ->
                finishWith(cafe.miafetta.tsuki.ui.AppUiVerification(this).run(redesignV4))
            argumentsForSurfaceCheck -> finish(Activity.RESULT_OK, Bundle().apply { putString("surfaceOptions", surfaceOptions()) })
            else -> runRenderCases()
        }
    }

    private fun finishWith(outcome: Pair<Boolean, String>) {
        val (passed, result) = outcome
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString(if (passed) "result" else "failure", result) })
    }

    private fun sendHostProbe(probe: String) {
        runOnMainSync {
            val manager = AppWidgetManager.getInstance(targetContext)
            val renderer = StatusWidgetRenderer(AvatarCache(File(targetContext.cacheDir, "official-avatars")))
            val repository = FileWidgetRepository(File(targetContext.noBackupFilesDir, "widgets"))
            StatusWidgetProvider.owned(targetContext).forEach { id ->
                val views = when (probe) {
                    "simple" -> android.widget.RemoteViews(targetContext.packageName, R.layout.probe_widget)
                    "xml" -> android.widget.RemoteViews(targetContext.packageName, R.layout.status_widget_native_v30_light)
                    else -> renderer.renderNative(targetContext, id, repository.read(id), false, manager.getAppWidgetOptions(id))
                }
                if (probe == "material" && repository.read(id)?.config?.theme == WidgetTheme.LIGHT)
                    views.setViewVisibility(R.id.status_native_surface, View.GONE)
                manager.updateAppWidget(id, views)
            }
        }
        finish(Activity.RESULT_OK, Bundle().apply { putString("result", "Host probe $probe sent") })
    }

    private fun surfaceOptions(): String {
        val manager = AppWidgetManager.getInstance(targetContext)
        return StatusWidgetProvider.owned(targetContext).joinToString("\n") { id ->
            val provider = checkNotNull(manager.getAppWidgetInfo(id)).provider
            @Suppress("DEPRECATION")
            val declared = targetContext.packageManager.getReceiverInfo(provider,
                android.content.pm.PackageManager.GET_META_DATA).metaData.getBoolean("supportBackgroundBlur", false)
            val options = manager.getAppWidgetOptions(id)
            val flags = listOf("widget_support_blur", "usingFolderGlassStyle", "background_blur_enable").joinToString { key ->
                @Suppress("DEPRECATION")
                val value = options.get(key)
                "$key=${value ?: "absent"} (${value?.javaClass?.simpleName ?: "none"})"
            }
            val theme = FileWidgetRepository(File(targetContext.noBackupFilesDir, "widgets")).read(id)?.config?.theme
            "Widget $id: provider=${provider.className}; supportBackgroundBlur=$declared; theme=$theme; " +
                "keys=${options.keySet().sorted().joinToString()}; $flags"
        }
    }

    private fun runRenderCases() {
        var failure: Throwable? = null
        var cases = 0
        runOnMainSync {
            try {
                val render = WidgetRenderCases(targetContext)
                render.avatarCrop()
                if (argumentsForLiveCheck) updateLiveWidgets(render.provider)
                cases = render.run()
            } catch (error: Throwable) { failure = error }
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", if (failure == null) "PASS $cases native render cases\n$liveResult\n" else
                "FAIL ${failure.stackTraceToString()}\n") })
    }

    // Re-renders the user's placed widgets from their saved records, optionally in another palette.
    private fun updateLiveWidgets(provider: AppWidgetProviderInfo) {
        val manager = AppWidgetManager.getInstance(targetContext)
        val repository = FileWidgetRepository(File(targetContext.noBackupFilesDir, "widgets"))
        val liveRenderer = StatusWidgetRenderer(AvatarCache(File(targetContext.cacheDir, "official-avatars")))
        val liveIds = manager.getAppWidgetIds(provider.provider)
        for (liveId in liveIds) {
            val options = manager.getAppWidgetOptions(liveId)
            val widths = listOf(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val heights = listOf(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val dimensions = (widths + heights).joinToString { "$it=${options.getInt(it)}" }
            val saved = repository.read(liveId)
            val preview = previewTheme?.let { theme -> saved?.copy(config = saved.config.copy(theme = theme)) } ?: saved
            val remote = if (nativeLive) liveRenderer.renderNative(targetContext, liveId, preview, false, options)
                else liveRenderer.render(targetContext, liveId, preview)
            remote.apply(targetContext, android.widget.FrameLayout(targetContext))
            manager.updateAppWidget(liveId, remote)
            liveResult = "Live widget $liveId updated; $dimensions"
        }
    }
}
