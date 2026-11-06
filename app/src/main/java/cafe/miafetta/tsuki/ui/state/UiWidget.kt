package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.core.WidgetSnapshot

internal data class UiWidget(val id: Int, val accountId: String, val roleId: String, val provider: String,
    val theme: String, val snapshot: WidgetSnapshot?, val failure: String, val simulateFailure: Boolean) {
    // Role IDs are unique only within one game, so the data source must match as well.
    fun bindsTo(role: UiRole) = provider == role.provider && accountId == role.accountId && roleId == role.id
}
