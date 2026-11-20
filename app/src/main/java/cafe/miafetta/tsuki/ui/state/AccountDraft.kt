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

    // The Cred whose Skland nickname may fill the name: only while the name is blank, so a name the user typed or an
    // updated account's prefilled name is never replaced.
    fun nameLookupCred(): String? = cred.trim().takeIf { name.isBlank() && it.matches(CRED_PATTERN) }

    // A nickname arrives after a network read: it is used only if the Cred is still the one it was read for and the
    // name is still blank. Returns whether the name was filled.
    fun offerName(cred: String, nickname: String?): Boolean {
        val value = nickname?.trim()?.take(NAME_LIMIT)?.trim().orEmpty()
        if (value.isEmpty() || name.isNotBlank() || this.cred.trim() != cred) return false
        name = value
        return true
    }

    companion object {
        const val NAME_LIMIT = 40
        val CRED_PATTERN = Regex("[!-~]{10,4096}")

        // Credentials must never enter saved instance state; the name and the account ID are not secret.
        val Saver = listSaver<AccountDraft, String>(save = { listOf(it.name, it.accountId.orEmpty()) },
            restore = { AccountDraft(it[0], it[1].ifEmpty { null }) })
    }
}

@Composable
internal fun rememberAccountDraft(): AccountDraft = rememberSaveable(saver = AccountDraft.Saver) { AccountDraft() }
