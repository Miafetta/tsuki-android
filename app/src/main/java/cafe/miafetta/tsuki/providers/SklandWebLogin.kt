package cafe.miafetta.tsuki.providers

import java.net.URI
import java.net.URISyntaxException
import org.json.JSONException
import org.json.JSONTokener

// In-app web sign-in on the official page (docs/DEVELOPMENT_CONSTRAINTS.md §3). The user signs in and completes any
// verification on that page; Tsuki only reads the resulting Cred once. The start page, hosts and storage key follow
// the official site as confirmed by the maintainer; update them together when the site changes.
internal object SklandWebLogin {
    const val START_URL = "https://www.skland.com/"
    private val HOSTS = setOf("skland.com", "hypergryph.com")
    private const val CRED_STORAGE_KEY = "SK_OAUTH_CRED_KEY"

    // Reads only the Cred entry of the current origin's storage; nothing else on the page is inspected.
    val READ_SCRIPT = "(function(){try{return window.localStorage.getItem('$CRED_STORAGE_KEY')}catch(e){return null}})()"

    // The official site serves phones an app download page, so the WebView asks for the desktop site the way a
    // browser's "desktop site" switch does: same Chrome major version, desktop platform token, no Mobile token. No
    // device fingerprint or other device information is changed.
    fun desktopUserAgent(defaultAgent: String): String {
        val major = Regex("""Chrome/(\d+)""").find(defaultAgent)?.groupValues?.get(1) ?: return defaultAgent
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    // The desktop page keeps width=device-width with maximum-scale=1 under a fixed 1200px header, so on a phone the
    // sign-in button sits off-screen and cannot be reached by scrolling or zooming. Only the viewport tag is rewritten
    // to lay the page out at the desktop width with zoom allowed; nothing else on the page is changed.
    private const val DESKTOP_WIDTH = 1200
    val VIEWPORT_SCRIPT = "(function(){var m=document.querySelector('meta[name=viewport]');" +
        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
        "var c='width=$DESKTOP_WIDTH';if(m.getAttribute('content')!==c)m.setAttribute('content',c);})()"

    // Main-frame navigation stays on the official hosts over https; anything else is blocked, not opened elsewhere.
    fun isAllowedNavigation(url: String): Boolean {
        val uri = try { URI(url) } catch (_: URISyntaxException) { return false }
        if (!"https".equals(uri.scheme, ignoreCase = true) || uri.rawUserInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return HOSTS.any { host == it || host.endsWith(".$it") }
    }

    // evaluateJavascript reports the value JSON-encoded: null, or a quoted string. Unquoted tokens are not strings.
    fun parseScriptResult(raw: String?): String? {
        if (raw == null || !raw.startsWith('"')) return null
        val value = try { JSONTokener(raw).nextValue() } catch (_: JSONException) { return null }
        val cred = (value as? String)?.trim() ?: return null
        return try {
            SklandClient.validateCred(cred)
            cred
        } catch (_: IllegalArgumentException) { null }
    }
}
