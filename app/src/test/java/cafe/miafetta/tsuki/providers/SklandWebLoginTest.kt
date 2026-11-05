package cafe.miafetta.tsuki.providers

import org.junit.Assert.*
import org.junit.Test

class SklandWebLoginTest {
    @Test fun navigationStaysOnOfficialHttpsHosts() {
        assertTrue(SklandWebLogin.isAllowedNavigation(SklandWebLogin.START_URL))
        for (url in listOf("https://skland.com/", "https://www.skland.com/login?from=app",
            "https://WWW.SKLAND.COM/", "https://www.skland.com./", "https://www.skland.com:443/",
            "https://user.hypergryph.com/")) {
            assertTrue(url, SklandWebLogin.isAllowedNavigation(url))
        }
        for (url in listOf("http://www.skland.com/", "https://skland.com.example.org/", "https://evilskland.com/",
            "https://example.org/?next=https://www.skland.com/", "https://www.skland.com@example.org/",
            "https://user:pass@www.skland.com/", "https://www.skland.com:8443/", "javascript:alert(1)",
            "intent://www.skland.com#Intent;scheme=https;end", "file:///sdcard/skland.com", "about:blank",
            "https:///skland.com", "not a url", "")) {
            assertFalse(url, SklandWebLogin.isAllowedNavigation(url))
        }
    }

    @Test fun desktopUserAgentKeepsTheChromeVersionAndDropsMobileTokens() {
        val phone = "Mozilla/5.0 (Linux; Android 16; Pixel 9 Build/BP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/141.0.7390.122 Mobile Safari/537.36"
        val desktop = SklandWebLogin.desktopUserAgent(phone)
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            desktop)
        for (token in listOf("Android", "Mobile", "wv", "Build/", "Pixel")) assertFalse(token, desktop.contains(token))
        assertEquals("Custom/1.0", SklandWebLogin.desktopUserAgent("Custom/1.0"))
    }

    @Test fun viewportScriptOnlyRewritesTheViewportTag() {
        val script = SklandWebLogin.VIEWPORT_SCRIPT
        assertTrue(script.contains("meta[name=viewport]") && script.contains("'width=1200'"))
        for (token in listOf("localStorage", "sessionStorage", "cookie", "fetch", "XMLHttpRequest", "location", "scale=1")) {
            assertFalse(token, script.contains(token))
        }
        assertFalse(SklandWebLogin.READ_SCRIPT.contains("setItem") || SklandWebLogin.READ_SCRIPT.contains("cookie"))
    }

    @Test fun scriptResultAcceptsOnlyAValidQuotedCred() {
        assertEquals("synthetic-cred-0001", SklandWebLogin.parseScriptResult("\"synthetic-cred-0001\""))
        assertEquals("synthetic-cred-0001", SklandWebLogin.parseScriptResult("\" synthetic-cred-0001 \""))
        for (raw in listOf(null, "", "null", "undefined", "\"short\"", "\"has space inside\"", "\"非ASCII凭据值0001\"",
            "12345678901", "{\"cred\":\"synthetic-cred-0001\"}", "[\"synthetic-cred-0001\"]", "\"" + "a".repeat(4097) + "\"",
            "\"unterminated")) {
            assertNull(raw, SklandWebLogin.parseScriptResult(raw))
        }
        assertEquals(4096, SklandWebLogin.parseScriptResult("\"" + "a".repeat(4096) + "\"")?.length)
    }
}
