package cafe.miafetta.tsuki.providers

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal object AndroidCredentialKey {
    @Synchronized fun get(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private const val ALIAS = "tsuki.skland.accounts.v1"
    @Synchronized fun delete() {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(ALIAS)
            }
        }
        catch (_: java.security.GeneralSecurityException) {
            throw cafe.miafetta.tsuki.core.DataSourceFailure(cafe.miafetta.tsuki.core.RefreshFailure.STORAGE)
        } catch (_: java.security.ProviderException) {
            throw cafe.miafetta.tsuki.core.DataSourceFailure(cafe.miafetta.tsuki.core.RefreshFailure.STORAGE)
        }
    }
}
