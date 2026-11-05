package cafe.miafetta.tsuki.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*

@Stable
internal class AccountDraft(name: String = "") {
    var name by mutableStateOf(name)
    var cred by mutableStateOf("")
    fun clear() {
        name = ""
        cred = ""
    }

    companion object {
        // Credentials must never enter saved instance state.
        val Saver = Saver<AccountDraft, String>(save = { it.name }, restore = { AccountDraft(it) })
    }
}

@Composable
internal fun rememberAccountDraft(): AccountDraft = rememberSaveable(saver = AccountDraft.Saver) { AccountDraft() }

