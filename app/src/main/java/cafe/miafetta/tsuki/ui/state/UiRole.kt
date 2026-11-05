package cafe.miafetta.tsuki.ui.state

import android.os.Bundle
import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.providers.SklandArknightsAdapter

internal data class UiRole(val accountId: String, val accountName: String, val id: String, val label: String,
    val provider: String = SklandArknightsAdapter.ID) {
    val key get() = "$accountId/$id"
    val name get() = label.substringBefore(" · ")
    fun extras() = Bundle().apply {
        putString(Storage.ACCOUNT_ID, accountId)
        putString(Storage.CHARACTER_ID, id)
    }
}
