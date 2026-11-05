package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import org.junit.Assert.*
import org.junit.Test

class WidgetRoleKeyTest {
    private val roles = listOf(role("a", "1"), role("a", "2"), role("b", "1"))

    @Test fun tappedWidgetSelectsItsBoundRole() {
        val widgets = listOf(widget(7, "a", "1"), widget(8, "b", "1"), widget(9, "a", "2"))
        assertEquals("b/1", widgetRoleKey(widgets, roles, 8))
        assertEquals("a/2", widgetRoleKey(widgets, roles, 9))
    }

    @Test fun unknownWidgetKeepsSelection() {
        assertNull(widgetRoleKey(listOf(widget(7, "a", "1")), roles, 70))
        assertNull(widgetRoleKey(emptyList(), roles, 7))
    }

    @Test fun unboundWidgetKeepsSelection() {
        val widgets = listOf(widget(7, "", ""), widget(8, "a", ""), widget(9, "", "1"))
        for (id in listOf(7, 8, 9)) assertNull(widgetRoleKey(widgets, roles, id))
    }

    @Test fun removedRoleKeepsSelection() {
        val widgets = listOf(widget(7, "a", "gone"), widget(8, "gone", "1"))
        assertNull(widgetRoleKey(widgets, roles, 7))
        assertNull(widgetRoleKey(widgets, roles, 8))
        // Before the first load, or after every account was removed, no role can be shown.
        assertNull(widgetRoleKey(listOf(widget(9, "a", "1")), emptyList(), 9))
    }

    @Test fun accountAndRoleMustMatchTogether() {
        // Role "2" exists only under account "a"; account "b" alone must not borrow it.
        assertNull(widgetRoleKey(listOf(widget(7, "b", "2")), roles, 7))
    }

    private fun role(account: String, id: String) = UiRole(account, "账号", id, "角色")

    private fun widget(id: Int, account: String, roleId: String) =
        UiWidget(id, account, roleId, SklandArknightsAdapter.ID, "DARK", null, "", false)
}
