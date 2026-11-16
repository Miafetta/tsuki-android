package cafe.miafetta.tsuki.widget

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

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
    private var lifecycle = false
    override fun onCreate(arguments: Bundle?) {
        appUi = arguments?.getString("appUi") == "true"
        redesignV4 = arguments?.getString("redesignV4")
        lifecycle = arguments?.getString("lifecycle") == "true"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        when {
            lifecycle -> finishWith(cafe.miafetta.tsuki.ui.windowLifecycleVerification(this))
            appUi || redesignV4 != null -> finishWith(cafe.miafetta.tsuki.ui.AppUiVerification(this).run(redesignV4))
            else -> runRenderCases()
        }
    }

    private fun finishWith(outcome: Pair<Boolean, String>) {
        val (passed, result) = outcome
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString(if (passed) "result" else "failure", result) })
    }

    private fun runRenderCases() {
        var failure: Throwable? = null
        var cases = 0
        runOnMainSync {
            try {
                val render = WidgetRenderCases(targetContext)
                render.avatarCrop()
                cases = render.run()
            } catch (error: Throwable) { failure = error }
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", if (failure == null) "PASS $cases native render cases\n" else
                "FAIL ${failure.stackTraceToString()}\n") })
    }
}
