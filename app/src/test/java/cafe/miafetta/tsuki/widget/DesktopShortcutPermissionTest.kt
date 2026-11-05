package cafe.miafetta.tsuki.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopShortcutPermissionTest {
    @Test fun allowedIsTheOnlyAutomaticallyGrantedMode() {
        assertEquals(DesktopShortcutPermission.State.GRANTED, DesktopShortcutPermission.fromMode(0))
        for (mode in listOf(1, 2)) {
            assertEquals(DesktopShortcutPermission.State.DENIED, DesktopShortcutPermission.fromMode(mode))
        }
    }

    @Test fun unreadableDefaultForegroundAndUnexpectedModesRequireConfirmation() {
        for (mode in listOf(null, 3, 4, -1, 10017)) {
            assertEquals(DesktopShortcutPermission.State.UNKNOWN, DesktopShortcutPermission.fromMode(mode))
        }
    }

    @Test fun vendorCheckCoversXiaomiBrandsWithoutAffectingOtherPhones() {
        for (brand in listOf("Xiaomi", "xiaomi", "Redmi", "POCO")) {
            assertTrue(DesktopShortcutPermission.isXiaomi(brand))
        }
        assertFalse(DesktopShortcutPermission.isXiaomi("Google"))
        assertFalse(DesktopShortcutPermission.isXiaomi("Samsung"))
    }
}
