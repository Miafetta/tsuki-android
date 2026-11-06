package cafe.miafetta.tsuki.ui.state

import android.os.Bundle
import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.ui.theme.Branding

// [serverName] is null for Arknights roles, whose label keeps the legacy "<name> · <server> · <uid>" form; the
// delimiter is parsed here and never displayed. Newer games store the nickname as the label and the server separately.
internal data class UiRole(val accountId: String, val accountName: String, val id: String, val label: String,
    val provider: String = SklandArknightsAdapter.ID, val serverName: String? = null) {
    // Arknights keeps the "<account>/<role>" key that installed versions saved as the selected role.
    val key get() = if (provider in LEGACY_KEY) "$accountId/$id" else "$provider/$accountId/$id"
    val name get() = if (serverName == null) label.substringBefore(" · ") else label
    val server get() = serverName ?: label.substringAfter(" · ", "未知").substringBefore(" · ")
    // Newer games encode the server in the role ID ("<server>~<role>"); only the role part is the game UID.
    val displayId get() = id.substringAfter('~')
    // Game tab of the role (Branding game ID).
    val game get() = gameOf(provider)
    fun extras() = Bundle().apply {
        putString(Storage.PROVIDER_ID, provider)
        putString(Storage.ACCOUNT_ID, accountId)
        putString(Storage.CHARACTER_ID, id)
    }

    private companion object {
        val LEGACY_KEY = setOf(SklandArknightsAdapter.ID, FakeArknightsAdapter.ID)
    }
}

internal fun gameOf(provider: String) =
    if (provider == SklandGame.ENDFIELD.providerId) Branding.ENDFIELD_ID else Branding.GAME_ID

// The game a saved role key belongs to; Arknights keys have no data source prefix.
internal fun gameOfKey(key: String) =
    if (key.startsWith("${SklandGame.ENDFIELD.providerId}/")) Branding.ENDFIELD_ID else Branding.GAME_ID
