package cafe.miafetta.tsuki.ui.state

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ConfigSheetTest {
    @Test fun saveRestoreKeepsNewEditAndClosingContent() {
        for (sheet in listOf(ConfigSheet.New("DARK", true), ConfigSheet.Edit(42, "LIGHT", true))) {
            for (state in listOf<ConfigSheet>(sheet, sheet.close())) {
                val restored = Json.decodeFromString<ConfigSheet>(Json.encodeToString(state))
                assertEquals(state, restored)
                assertEquals(sheet, restored.content)
            }
        }
        assertNull(ConfigSheet.Closed().content)
    }
}
