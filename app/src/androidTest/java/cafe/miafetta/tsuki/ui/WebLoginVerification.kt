package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.providers.SklandWebLogin
import cafe.miafetta.tsuki.ui.state.UiPreferences

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.edit
import androidx.core.view.children
import cafe.miafetta.tsuki.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Web login on the production app against the official start page; nothing is signed in and no account is saved. It
// checks what the user cannot see: cookies and page storage are wiped when the page closes but survive a configuration
// change, system back steps back inside the page first, and a failed load offers a retry. The user's clipboard and
// theme are put back afterwards. Each step loads the official page at most once.
private const val CHECK_KEY = "tsuki_wipe_check"
// The space keeps it from looking like a Cred, so the editor never sends it to read a nickname for a blank name.
private const val PASTED = "tsuki-paste-check 0123456789"

internal fun AppUiVerification.verifyWebLogin(host: MainActivity, folder: File): String {
    val clipboard = checkNotNull(targetContext.getSystemService(ClipboardManager::class.java))
    val preferences = targetContext.getSharedPreferences(UiPreferences.FILE, 0)
    val theme = preferences.getString("theme", null)
    var clip: ClipData? = null
    runOnMainSync { clip = clipboard.primaryClip }
    try {
        openUpdateEditor(host)
        verifyPaste(host, clipboard)
        openWebLogin(host, folder, "light")
        plantSessionData(host)
        verifyInPageBack(host)
        val recreated = recreate(host)
        awaitPane(recreated, "网页登录")
        awaitOfficialPage(recreated)
        check(cookiePresent() && markerExists() && localValue(recreated) == "\"1\"") { "Session lost on recreation" }
        verifyLoadFailure(recreated, folder)
        click("返回")
        awaitPane(recreated, "添加账号")
        awaitWiped()
        preferences.edit(commit = true) { putString("theme", "DARK") }
        val dark = recreate(recreated)
        awaitPane(dark, "添加账号")
        openWebLogin(dark, folder, "dark")
        check(!cookiePresent() && localValue(dark) == "null") { "Web data survived leaving the page" }
        shell("input keyevent BACK")
        awaitPane(dark, "添加账号")
        awaitWiped()
        shell("input keyevent BACK")
        hostRunner.settleUi()
        return "PASS: paste and Update placeholder; official page light/dark; in-page back; session kept on recreation " +
            "and wiped on leaving; load failure and retry; re-entry starts clean"
    } finally {
        preferences.edit(commit = true) { if (theme == null) remove("theme") else putString("theme", theme) }
        runOnMainSync { clip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip() }
    }
}

// First half of the killed-process check: leaves the login page open with data on disk, then kills this process.
internal fun AppUiVerification.killDuringWebLogin(host: MainActivity, folder: File): Nothing {
    openUpdateEditor(host)
    openWebLogin(host, folder, "kill")
    plantSessionData(host)
    android.os.Process.killProcess(android.os.Process.myPid())
    error("Process survived killProcess")
}

// Second half: a fresh launch of the production app wipes what the killed login page left behind.
internal fun AppUiVerification.verifyWipedAfterKill(): String {
    val deadline = android.os.SystemClock.uptimeMillis() + 5000
    while ((cookiePresent() || markerExists()) && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(200)
    check(!cookiePresent() && !markerExists()) { "Web data survived the killed login page" }
    return "PASS: web data left by a killed login page is wiped on the next launch"
}

private fun AppUiVerification.openUpdateEditor(host: MainActivity) {
    runOnMainSync { check(host.clickSemantics { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-tab:2" }) }
    hostRunner.settleUi()
    click("账号菜单")
    hostRunner.settleAccessibility()
    click("更新 Cred")
    awaitPane(host, "添加账号")
    check(find("留空则不修改当前 Cred", exact = true) != null) { "Update placeholder missing" }
    check(credText(host).isEmpty()) { "Update must not prefill the stored Cred" }
}

private fun AppUiVerification.verifyPaste(host: MainActivity, clipboard: ClipboardManager) {
    runOnMainSync { clipboard.setPrimaryClip(ClipData.newPlainText("check", PASTED)) }
    click("粘贴 Cred")
    frames(host)
    // A hidden field exposes only its masked text, so the value is read once it is shown.
    click("显示 Cred")
    frames(host)
    check(credText(host) == PASTED && find(PASTED, exact = true) != null) { "Paste did not fill the Cred" }
    click("隐藏 Cred")
    runOnMainSync {
        val field = host.semanticsNodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "account-cred" }
        checkNotNull(field.config.getOrNull(SemanticsActions.SetText)?.action).invoke(AnnotatedString(""))
    }
    frames(host)
    check(credText(host).isEmpty() && find("留空则不修改当前 Cred", exact = true) != null)
}

private fun AppUiVerification.openWebLogin(host: Activity, folder: File, name: String) {
    click("在森空岛网页登录")
    awaitPane(host, "网页登录")
    check(markerExists()) { "Login page did not mark its data for cleanup" }
    awaitOfficialPage(host)
    frames(host)
    capture(folder, "web-login-official-$name")
}

private fun AppUiVerification.plantSessionData(host: Activity) {
    val view = webView(host)
    runOnMainSync {
        CookieManager.getInstance().setCookie(SklandWebLogin.START_URL, "$CHECK_KEY=1; Max-Age=600")
        CookieManager.getInstance().flush()
        view.evaluateJavascript("localStorage.setItem('$CHECK_KEY', '1')", null)
    }
    check(cookiePresent() && localValue(host) == "\"1\"") { "Check data was not stored" }
}

private fun AppUiVerification.verifyInPageBack(host: Activity) {
    val view = webView(host)
    runOnMainSync { view.loadUrl("${SklandWebLogin.START_URL}?tsuki=history") }
    awaitOfficialPage(host) { "tsuki=history" in it }
    var back = false
    runOnMainSync { back = view.canGoBack() }
    check(back) { "Second page left no history" }
    shell("input keyevent BACK")
    awaitOfficialPage(host) { "tsuki=history" !in it }
    runOnMainSync { back = view.canGoBack() }
    check(!back && find("网页登录", exact = true) != null) { "System back did not step back inside the page" }
}

private fun AppUiVerification.verifyLoadFailure(host: Activity, folder: File) {
    val view = webView(host)
    runOnMainSync { view.loadUrl("https://tsuki-check.invalid/") }
    framesUntilLong(host) { find("重试", exact = true) != null }
    capture(folder, "web-login-failure")
    click("重试")
    framesUntilLong(host) { find("网页加载失败，请检查网络后重试。", exact = true) != null }
    runOnMainSync { view.loadUrl(SklandWebLogin.START_URL) }
    awaitOfficialPage(host)
    framesUntilLong(host) { find("重试", exact = true) == null }
}

private fun AppUiVerification.recreate(host: Activity): Activity {
    runOnMainSync { host.recreate() }
    val deadline = android.os.SystemClock.uptimeMillis() + 10_000
    while (hostRunner.uiActivity === host && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
    val next = checkNotNull(hostRunner.uiActivity?.takeIf { it !== host }) { "Activity was not recreated" }
    hostRunner.settleUi()
    return next
}

private fun AppUiVerification.awaitPane(host: Activity, title: String) {
    framesUntilLong(host) {
        var ready = false
        runOnMainSync {
            ready = host.semanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.PaneTitle) == title &&
                    !node.config.contains(SemanticsProperties.HideFromAccessibility)
            }
        }
        ready
    }
    hostRunner.settleAccessibility()
}

private fun AppUiVerification.awaitOfficialPage(host: Activity, url: (String) -> Boolean = { true }) =
    framesUntilLong(host, 30_000) {
        var loaded = false
        runOnMainSync {
            val view = host.window.decorView.findWebView()
            val current = view?.url.orEmpty()
            loaded = view != null && view.progress == 100 && current.startsWith(SklandWebLogin.START_URL) && url(current)
        }
        loaded
    }

private fun AppUiVerification.awaitWiped() {
    val deadline = android.os.SystemClock.uptimeMillis() + 5000
    while ((cookiePresent() || markerExists()) && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(200)
    check(!cookiePresent() && !markerExists()) { "Leaving the login page did not wipe its data" }
}

private fun AppUiVerification.framesUntilLong(host: Activity, millis: Long = 10_000, condition: () -> Boolean) {
    val deadline = android.os.SystemClock.uptimeMillis() + millis
    while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) frames(host, 12)
    check(condition()) { "UI did not reach the requested state" }
}

private fun AppUiVerification.credText(host: Activity): String {
    var text = ""
    runOnMainSync {
        val field = host.semanticsNodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "account-cred" }
        text = field.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
    }
    return text
}

private fun AppUiVerification.webView(host: Activity): WebView {
    var view: WebView? = null
    runOnMainSync { view = host.window.decorView.findWebView() }
    return checkNotNull(view) { "Login WebView missing" }
}

// The page's own storage as JSON: "\"1\"" for the check value, "null" when the storage was wiped.
private fun AppUiVerification.localValue(host: Activity): String {
    val view = webView(host)
    val done = CountDownLatch(1)
    var value = ""
    runOnMainSync {
        view.evaluateJavascript("localStorage.getItem('$CHECK_KEY')") {
            value = it
            done.countDown()
        }
    }
    check(done.await(5, TimeUnit.SECONDS)) { "Page storage did not answer" }
    return value
}

private fun AppUiVerification.cookiePresent(): Boolean {
    var cookies = ""
    runOnMainSync { cookies = CookieManager.getInstance().getCookie(SklandWebLogin.START_URL).orEmpty() }
    return CHECK_KEY in cookies
}

private fun AppUiVerification.markerExists() = File(targetContext.noBackupFilesDir, "web-login-dirty").exists()

private fun View.findWebView(): WebView? =
    this as? WebView ?: (this as? ViewGroup)?.children?.firstNotNullOfOrNull { it.findWebView() }
