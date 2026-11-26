package cafe.miafetta.tsuki.core

// Why an account could not be added or updated. These are rules of the stored accounts, not broken requests, so the
// account editor names them (StorageCalls.accountProblemText) instead of showing the generic storage error.
internal enum class AccountProblem {
    // The account limit (FileAccountRepository.MAX_ACCOUNTS) is reached.
    ACCOUNT_LIMIT,
    // "更新 Cred" was given the Cred of another Skland account than the one being updated.
    ACCOUNT_MISMATCH,
    // The Cred's roles already belong to another stored account.
    ACCOUNT_CONFLICT,
    // The account being updated was removed in the meantime.
    ACCOUNT_GONE,
}

// Still an IllegalArgumentException: every caller that treats an invalid account operation as an invalid request keeps
// doing so, and only WidgetStorageProvider reads the problem to report it.
internal class AccountProblemException(val problem: AccountProblem, message: String) : IllegalArgumentException(message)
