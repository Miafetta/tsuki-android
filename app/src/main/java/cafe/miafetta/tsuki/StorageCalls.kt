package cafe.miafetta.tsuki

import cafe.miafetta.tsuki.StorageContract as Storage

import android.content.Context
import android.os.Bundle
import cafe.miafetta.tsuki.core.AccountProblem
import cafe.miafetta.tsuki.providers.FileAccountRepository
import cafe.miafetta.tsuki.ui.theme.Branding
import cafe.miafetta.tsuki.widget.WidgetStorageProvider

internal fun Context.storageCall(method: String, extras: Bundle = Bundle()): Bundle = try {
    contentResolver.call(WidgetStorageProvider.URI, method, null, extras)
        ?: Bundle().apply { putBoolean(Storage.ERROR, true) }
} catch (_: RuntimeException) { Bundle().apply { putBoolean(Storage.ERROR, true) } }

internal fun failureMessage(failure: String?): Int = when (failure) {
    "AUTH_EXPIRED" -> R.string.error_auth
    "RATE_LIMIT" -> R.string.error_rate
    "CLOCK_SKEW" -> R.string.error_clock
    "INVALID_DATA" -> R.string.error_data
    "NETWORK" -> R.string.error_network
    "SERVER" -> R.string.error_server
    else -> R.string.status_storage_error
}

// The account editor's words for an account rule that refused the account (AccountProblem); null for any other
// failure, which keeps failureMessage. App texts stay in Kotlin (docs/PROJECT_STRUCTURE.md §3).
internal fun accountProblemText(failure: String?): String? = when (failure) {
    AccountProblem.ACCOUNT_LIMIT.name -> "最多添加 ${FileAccountRepository.MAX_ACCOUNTS} 个账号，请先删除不再使用的账号。"
    AccountProblem.ACCOUNT_MISMATCH.name ->
        "这个 Cred 属于另一个${Branding.PLATFORM}账号，不能用来更新当前账号；如需添加它，请使用“添加账号”。"
    AccountProblem.ACCOUNT_CONFLICT.name -> "这个 Cred 的角色已属于另一个已添加的账号。"
    AccountProblem.ACCOUNT_GONE.name -> "这个账号已被删除，请返回后重新添加。"
    else -> null
}
