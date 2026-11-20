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

    @Test fun dataSourceMustMatchToo() {
        // An Endfield widget whose role digits equal an Arknights role must not select the Arknights role.
        val endfieldRole = UiRole("a", "账号", "1~1", "管理员", "skland.endfield", "官服")
        val widget = UiWidget(7, "a", "1~1", "skland.endfield", "DARK", null, "", false)
        assertNull(widgetRoleKey(listOf(widget.copy(roleId = "1")), roles + endfieldRole, 7))
        assertEquals("skland.endfield/a/1~1", widgetRoleKey(listOf(widget), roles + endfieldRole, 7))
    }

    @Test fun roleKeysNamesAndServers() {
        // Arknights keeps the key installed versions saved as the selected role, and its legacy label form.
        val arknights = UiRole("a", "", "000000001", "测试博士 · 官服 · 000000001")
        assertEquals("a/000000001", arknights.key)
        assertEquals("测试博士", arknights.name)
        assertEquals("官服", arknights.server)
        assertEquals("000000001", arknights.displayId)
        val endfield = UiRole("a", "", "1~000000001", "测试管理员", "skland.endfield", "官服")
        assertEquals("skland.endfield/a/1~000000001", endfield.key)
        assertEquals("测试管理员", endfield.name)
        assertEquals("官服", endfield.server)
        assertEquals("000000001", endfield.displayId)
    }

    @Test fun aReminderRoleMapsToTheSameKeyAsTheListedRole() {
        // A reminder notification names its role by IDs only; the overview must find the listed role by that key.
        val arknights = UiRole("a", "", "000000001", "测试博士 · 官服 · 000000001")
        assertEquals(arknights.key, roleKey(SklandArknightsAdapter.ID, "a", "000000001"))
        val endfield = UiRole("a", "", "1~000000001", "测试管理员", "skland.endfield", "官服")
        assertEquals(endfield.key, roleKey("skland.endfield", "a", "1~000000001"))
        assertNotEquals(roleKey(SklandArknightsAdapter.ID, "a", "1"), roleKey("skland.endfield", "a", "1"))
    }

    private fun role(account: String, id: String) = UiRole(account, "账号", id, "角色")

    private fun widget(id: Int, account: String, roleId: String) =
        UiWidget(id, account, roleId, SklandArknightsAdapter.ID, "DARK", null, "", false)
}
