package cafe.miafetta.tsuki.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*

// accountId is set when the editor was opened from "更新 Cred": a blank Cred then keeps the account's stored one.
@Stable
internal class AccountDraft(name: String = "", accountId: String? = null) {
    var name by mutableStateOf(name)
    var cred by mutableStateOf("")
    var accountId by mutableStateOf(accountId)
    fun clear() {
        name = ""
        cred = ""
        accountId = null
    }

    companion object {
        // Credentials must never enter saved instance state; the name and the account ID are not secret.
        val Saver = listSaver<AccountDraft, String>(save = { listOf(it.name, it.accountId.orEmpty()) },
            restore = { AccountDraft(it[0], it[1].ifEmpty { null }) })
    }
}

@Composable
internal fun rememberAccountDraft(): AccountDraft = rememberSaveable(saver = AccountDraft.Saver) { AccountDraft() }
