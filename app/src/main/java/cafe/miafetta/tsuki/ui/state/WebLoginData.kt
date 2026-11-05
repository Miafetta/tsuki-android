package cafe.miafetta.tsuki.ui.state

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

// Web login leaves cookies and page storage, which can hold a Cred in plain text, in WebView's private data
// directory. They are wiped when the login page closes; the marker covers a process killed while it was open.
// Call on the main thread: CookieManager and WebStorage require a Looper thread.
internal object WebLoginData {
    private const val MARKER = "web-login-dirty"
    private val open = AtomicInteger()

    fun opened(context: Context) {
        open.incrementAndGet()
        File(context.noBackupFilesDir, MARKER).createNewFile()
    }

    fun closed() {
        open.decrementAndGet()
    }

    fun wipe(context: Context, webView: WebView? = null) {
        webView?.apply {
            stopLoading()
            clearHistory()
            clearFormData()
            clearCache(true)
        }
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies { cookies.flush() }
        WebStorage.getInstance().deleteAllData()
        File(context.noBackupFilesDir, MARKER).delete()
    }

    // Loads WebView only when an earlier login page did not get to clean up.
    fun wipeIfStale(context: Context) {
        if (open.get() == 0 && File(context.noBackupFilesDir, MARKER).exists()) wipe(context)
    }
}
