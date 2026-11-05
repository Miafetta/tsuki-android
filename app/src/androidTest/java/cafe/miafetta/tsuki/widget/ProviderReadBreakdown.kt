package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.StorageContract as Storage

import android.content.Context
import android.os.Bundle
import cafe.miafetta.tsuki.core.FileWidgetRepository
import cafe.miafetta.tsuki.providers.AndroidCredentialKey
import cafe.miafetta.tsuki.providers.CredentialCipher
import cafe.miafetta.tsuki.providers.FileAccountRepository
import java.io.File

// Read-only timing of the steps behind one overview, characters or dashboard call, run in this process against the files
// :widgetProvider uses. Nothing is written, no key is created, and no decrypted value leaves this function.
internal fun providerReadBreakdown(context: Context): String {
    val file = File(context.noBackupFilesDir, "accounts/v1.bin")
    if (!file.exists()) return "SKIP: no stored account"
    val bytes = file.readBytes()
    val key = AndroidCredentialKey.get()
    val repository = FileWidgetRepository(File(context.noBackupFilesDir, "widgets"))
    val ids = StatusWidgetProvider.owned(context)
    val steps = linkedMapOf(
        "keystore-key" to { AndroidCredentialKey.get() },
        "file-read" to { file.readBytes() },
        // A complete Keystore operation; a begun but unfinished one could crowd out the provider's own operations.
        "decrypt" to { CredentialCipher { key }.decrypt(bytes).fill(0) },
        "accounts-list" to { FileAccountRepository(file, CredentialCipher(AndroidCredentialKey::get)).list() },
        "owned-ids" to { StatusWidgetProvider.owned(context) },
        "widget-records" to { ids.forEach { repository.read(it) } },
        "ipc-overview" to { call(context, Storage.OVERVIEW) },
        "ipc-characters" to { call(context, Storage.CHARACTERS) },
    )
    return "PASS: provider read breakdown (median of 9 after 2 warm-ups, ms; widgets=${ids.size})\n" +
        steps.entries.joinToString("\n") { (name, step) -> "$name ${"%.2f".format(median(step))}" }
}

private fun call(context: Context, method: String) {
    val result = context.contentResolver.call(WidgetStorageProvider.URI, method, null, Bundle())
    check(result != null && !result.getBoolean(Storage.ERROR)) { "$method failed" }
}

private fun median(step: () -> Any?): Double {
    repeat(2) { step() }
    val samples = DoubleArray(9) {
        val start = System.nanoTime()
        step()
        (System.nanoTime() - start) / 1_000_000.0
    }
    samples.sort()
    return samples[4]
}
