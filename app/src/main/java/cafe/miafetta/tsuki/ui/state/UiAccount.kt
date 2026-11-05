package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage

internal data class UiAccount(val id: String, val name: String, val roles: List<UiRole>)
