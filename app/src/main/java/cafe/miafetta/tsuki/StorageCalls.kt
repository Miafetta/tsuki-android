package cafe.miafetta.tsuki

import cafe.miafetta.tsuki.StorageContract as Storage

import android.content.Context
import android.os.Bundle
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
