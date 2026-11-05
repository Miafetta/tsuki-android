package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.providers.SklandWebLogin
import cafe.miafetta.tsuki.ui.state.WebLoginData

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay

// Official web sign-in (docs/DEVELOPMENT_CONSTRAINTS.md §3). The WebView exposes no JavaScript interface, keeps
// main-frame navigation on SklandWebLogin's hosts and reads the Cred once. Its data is wiped when the page leaves,
// except across a configuration change, so the official session survives rotation.
@Composable
internal fun WebLoginPage(activity: Activity, onCred: (String) -> Unit, onBack: () -> Unit,
    startUrl: String = SklandWebLogin.START_URL) {
    val transition = LocalNavTransitionScope.current
    val active by remember(transition) { derivedStateOf { !transition.isRunning && transition.relativeDepth == 0f } }
    val state = remember { WebLoginState() }
    val currentOnCred by rememberUpdatedState(onCred)
    // System back steps back inside the page first; the top bar button always leaves.
    PredictiveBackHandler(enabled = active && state.canGoBack, onProgress = { events -> events.collect {} },
        onCommit = { state.view?.goBack() }, onCancel = {})
    SecondaryScaffold("网页登录", blurEnabled = false, active = active, onBack = onBack, scrollable = false,
        modifier = Modifier.imePadding().testTag("web-login-page")) {
        if (state.failed) LoadFailure(state::retry)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            WebLoginView(activity, startUrl, state) { currentOnCred(it) }
            if (state.progress < 100) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), progress = state.progress / 100f)
            }
        }
    }
}

@Stable
private class WebLoginState {
    var view by mutableStateOf<WebView?>(null)
    var progress by mutableIntStateOf(0)
    var failed by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var delivered = false
    // Whether the current page load has been zoomed out to fit; later zooming is left to the user.
    var fitted = false

    fun retry() {
        failed = false
        view?.reload()
    }
}

@Composable
private fun WebLoginView(activity: Activity, startUrl: String, state: WebLoginState, onCred: (String) -> Unit) {
    val background = MiuixTheme.colorScheme.surface.toArgb()
    // The desktop page signs in through a dialog without navigating, so the Cred is also looked for once a second.
    // This only reads the page's own storage on the device and sends no request.
    LaunchedEffect(state) {
        while (!state.delivered) {
            delay(CRED_CHECK_MILLIS)
            state.view?.let {
                fitDesktop(it, state)
                readCred(it, state, onCred)
            }
        }
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        WebLoginData.opened(context)
        loginWebView(context, background, LoginClient(state, onCred), LoginChrome(state)).also {
            state.view = it
            it.loadUrl(startUrl)
        }
    }, onRelease = { view ->
        state.view = null
        if (!activity.isChangingConfigurations) WebLoginData.wipe(activity, view)
        view.destroy()
        WebLoginData.closed()
    })
}

@Composable
private fun LoadFailure(onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("网页加载失败，请检查网络后重试。", Modifier.weight(1f), style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error)
        TextButton("重试", onRetry)
    }
}

private const val CRED_CHECK_MILLIS = 1000L
private const val FIT_DELAY_MILLIS = 150L

// zoomBy clamps to the page's limits, so a small factor lands on the minimum scale: the page width fits the screen.
private const val FIT_ZOOM_FACTOR = 0.02f

// The official page needs JavaScript and DOM storage; no JavaScript interface is ever added to this WebView.
// The desktop site is laid out for a wide window: it opens zoomed out to fit and can be pinch-zoomed.
@SuppressLint("SetJavaScriptEnabled")
private fun loginWebView(context: Context, background: Int, client: WebViewClient, chrome: WebChromeClient) =
    WebView(context).apply {
        isSaveEnabled = false
        setBackgroundColor(background)
        settings.apply {
            userAgentString = SklandWebLogin.desktopUserAgent(WebSettings.getDefaultUserAgent(context))
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
        webViewClient = client
        webChromeClient = chrome
    }

private class LoginClient(private val state: WebLoginState, private val onCred: (String) -> Unit) : WebViewClient() {
    // Returning true cancels the navigation; subframes (verification widgets) are left to the page.
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.isForMainFrame && !SklandWebLogin.isAllowedNavigation(request.url.toString())

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        state.failed = false
        state.fitted = false
    }

    override fun onPageCommitVisible(view: WebView, url: String?) = fitDesktop(view, state)

    override fun onPageFinished(view: WebView, url: String?) {
        fitDesktop(view, state)
        readCred(view, state, onCred)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        state.canGoBack = view.canGoBack()
        readCred(view, state, onCred)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) state.failed = true
    }
}

// Lays an official page out at the desktop width (see SklandWebLogin.VIEWPORT_SCRIPT), then once per load zooms out
// to the smallest allowed scale, which fits the page width to the screen. The rewrite is idempotent.
private fun fitDesktop(view: WebView, state: WebLoginState) {
    if (!SklandWebLogin.isAllowedNavigation(view.url.orEmpty())) return
    view.evaluateJavascript(SklandWebLogin.VIEWPORT_SCRIPT) {
        if (!state.fitted && state.view === view) {
            state.fitted = true
            // The new viewport needs a layout pass before the minimum scale reflects it.
            view.postDelayed({ if (state.view === view) view.zoomBy(FIT_ZOOM_FACTOR) }, FIT_DELAY_MILLIS)
        }
    }
}

// Reads only while an official page is shown and delivers at most one Cred per page.
private fun readCred(view: WebView, state: WebLoginState, onCred: (String) -> Unit) {
    if (state.delivered || !SklandWebLogin.isAllowedNavigation(view.url.orEmpty())) return
    view.evaluateJavascript(SklandWebLogin.READ_SCRIPT) { raw ->
        val cred = SklandWebLogin.parseScriptResult(raw)
        if (cred != null && !state.delivered) {
            state.delivered = true
            WebLoginData.wipe(view.context, view)
            onCred(cred)
        }
    }
}

private class LoginChrome(private val state: WebLoginState) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        state.progress = newProgress
    }
}
