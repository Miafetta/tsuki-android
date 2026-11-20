package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.state.UiAccount
import cafe.miafetta.tsuki.ui.state.UiRole

import org.junit.Assert.*
import org.junit.Test

// The shared account row: an account goes by the same name on "我的" and on its reminder page.
class AccountRowTest {
    @Test fun anAccountGoesByItsAliasOrItsPlatform() {
        val role = UiRole("a", "", "000000001", "测试博士 · 官服 · 000000001")
        assertEquals("主账号", accountTitle(UiAccount("a", "主账号", listOf(role))))
        // Unnamed: the platform, never one of its roles.
        assertEquals("森空岛账号", accountTitle(UiAccount("a", "", listOf(role))))
        assertEquals("森空岛账号", accountTitle(UiAccount("a", "  ", emptyList())))
    }
}
