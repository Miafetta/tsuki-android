package cafe.miafetta.tsuki

import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import org.junit.Assert.*
import org.junit.Test

// The role a reminder notification opens: what its Intent carries and how MainActivity reads it back. Intents
// themselves need a device; the extras go through the same functions as a map.
class ReminderLaunchTest {
    private val arknights = ReminderSubject(SklandArknightsAdapter.ID, "account-a", "000000001")
    private val endfield = ReminderSubject(SklandGame.ENDFIELD.providerId, "account-a", "1~0000000011")

    @Test fun theRoleSurvivesTheExtras() {
        for (subject in listOf(arknights, endfield)) {
            val extras = ReminderLaunch.extras(subject)
            assertEquals(setOf(ReminderLaunch.PROVIDER_ID, ReminderLaunch.ACCOUNT_ID, ReminderLaunch.CHARACTER_ID), extras.keys)
            assertEquals(subject, ReminderLaunch.role(extras::get))
        }
    }

    @Test fun missingBlankOrForeignExtrasOpenNoRole() {
        val extras = ReminderLaunch.extras(endfield)
        for (key in extras.keys) {
            assertNull(ReminderLaunch.role((extras - key)::get))
            assertNull(ReminderLaunch.role((extras + (key to ""))::get))
            assertNull(ReminderLaunch.role((extras + (key to " "))::get))
        }
        // A data source that is no Skland game, including the debug fixture, and an ID longer than any role's.
        for (provider in listOf("skland.unknown", FakeArknightsAdapter.ID, "")) {
            assertNull(ReminderLaunch.role((extras + (ReminderLaunch.PROVIDER_ID to provider))::get))
        }
        assertNull(ReminderLaunch.role((extras + (ReminderLaunch.ACCOUNT_ID to "a".repeat(81)))::get))
        // Any other launch of MainActivity carries none of the keys.
        assertNull(ReminderLaunch.role { null })
    }

    @Test fun roleKeysTellRolesApart() {
        val keys = listOf(arknights, endfield, arknights.copy(accountId = "account-b"), arknights.copy(characterId = "000000002"))
            .map(ReminderLaunch::roleKey)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("skland/account-a/000000001", ReminderLaunch.roleKey(arknights))
    }
}
