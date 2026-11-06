package cafe.miafetta.tsuki.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

// Role caches of several games: Arknights keeps the top-level files of installed versions, other games get folders.
class RoleDataStoreTest {
    private val snapshot = WidgetSnapshot("测试角色", listOf(WidgetMetric("理智", 10, 100)), 1_700_000_000_000)

    @Test fun arknightsKeepsTheInstalledPathAndOtherGamesUseTheirOwnFolder() = withFolder { folder ->
        val store = RoleDataStore(folder, "skland")
        store.write("skland", "account-a", "000000001", RoleData(snapshot))
        store.write("skland.endfield", "account-a", "1~000000001", RoleData(snapshot, RefreshFailure.NETWORK))
        assertTrue(File(folder, "account-a.000000001.json").isFile)
        assertTrue(File(folder, "skland.endfield/account-a.1~000000001.json").isFile)
        assertNull(store.read("skland", "account-a", "000000001").failure)
        assertEquals(RefreshFailure.NETWORK, store.read("skland.endfield", "account-a", "1~000000001").failure)
    }

    @Test fun cacheWrittenByAnInstalledVersionIsStillRead() = withFolder { folder ->
        // 2.1.1 wrote exactly this file name and schema.
        File(folder, "account-a.000000001.json").writeText(JSONText.legacy(snapshot))
        assertEquals(snapshot, RoleDataStore(folder, "skland").read("skland", "account-a", "000000001").snapshot)
    }

    @Test fun clearingAnAccountRemovesItsRolesOfEveryGameOnly() = withFolder { folder ->
        val store = RoleDataStore(folder, "skland")
        store.write("skland", "account-a", "000000001", RoleData(snapshot))
        store.write("skland.endfield", "account-a", "1~000000001", RoleData(snapshot))
        store.write("skland.endfield", "account-b", "1~000000001", RoleData(snapshot))
        store.clear("account-a")
        assertNull(store.read("skland", "account-a", "000000001").snapshot)
        assertNull(store.read("skland.endfield", "account-a", "1~000000001").snapshot)
        assertEquals(snapshot, store.read("skland.endfield", "account-b", "1~000000001").snapshot)
        store.clear()
        assertNull(store.read("skland.endfield", "account-b", "1~000000001").snapshot)
    }

    @Test fun arknightsRoleIdsStayNumeric() = withFolder { folder ->
        assertThrows(IllegalArgumentException::class.java) {
            RoleDataStore(folder, "skland").write("skland", "account-a", "1~000000001", RoleData())
        }
    }

    private object JSONText {
        fun legacy(snapshot: WidgetSnapshot) = org.json.JSONObject().put("version", 1)
            .put("snapshot", SnapshotJson.write(snapshot)).put("retryAt", 0).toString()
    }

    private fun withFolder(block: (File) -> Unit) {
        val folder = Files.createTempDirectory("tsuki-role-store").toFile()
        try { block(folder) } finally { folder.deleteRecursively() }
    }
}
