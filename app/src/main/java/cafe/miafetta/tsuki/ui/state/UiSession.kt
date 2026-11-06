package cafe.miafetta.tsuki.ui.state

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import kotlinx.serialization.json.Json

// UI state that survives recreation; pendingPin holds widget config extras only, never credentials.
@Stable
internal class UiSession(page: Int, selectedKey: String, configSheet: ConfigSheet, selectedGame: String = gameOfKey(selectedKey)) {
    var page by mutableIntStateOf(page)
    var catalog by mutableIntStateOf(0)
    var selectedKey by mutableStateOf(selectedKey)
    var selectedGame by mutableStateOf(selectedGame)
    var configSheet by mutableStateOf(configSheet)
    var pendingPin by mutableStateOf<Bundle?>(null)
    var permissionDialog by mutableStateOf(false)
    var waitingSettings by mutableStateOf(false)

    companion object {
        val Saver = listSaver<UiSession, Any?>(
            save = {
                listOf(it.page, it.catalog, it.selectedKey, Json.encodeToString(it.configSheet), it.pendingPin,
                    it.permissionDialog, it.waitingSettings, it.selectedGame)
            },
            restore = {
                UiSession(it[0] as Int, it[2] as String, Json.decodeFromString<ConfigSheet>(it[3] as String),
                    it[7] as String).apply {
                    catalog = it[1] as Int
                    pendingPin = it[4] as Bundle?
                    permissionDialog = it[5] as Boolean
                    waitingSettings = it[6] as Boolean
                }
            },
        )
    }
}
