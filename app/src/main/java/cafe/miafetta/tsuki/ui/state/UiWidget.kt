package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.StorageContract as Storage
import cafe.miafetta.tsuki.core.WidgetSnapshot

internal data class UiWidget(val id: Int, val accountId: String, val roleId: String, val provider: String,
    val theme: String, val snapshot: WidgetSnapshot?, val failure: String, val simulateFailure: Boolean)

