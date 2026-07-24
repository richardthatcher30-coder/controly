package com.homecontrol.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "controly_secure_key_storage_master_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12
private const val AES_KEY_SIZE_BITS = 256

/**
 * Same AES-256-GCM-in-AndroidKeystore approach as `core:security`'s
 * `KeystoreCipher` (that module isn't depended on here to avoid coupling
 * this new module to one being retired later, per the KMP migration plan —
 * `AdbKeyStore.kt` switches from `KeystoreCipher` to this class in the phase
 * that ports `plugin-androidtv`). One ciphertext-per-alias file under
 * [storageDir]; the AES key itself never leaves the Keystore/TEE.
 */
actual class SecureKeyStorage actual constructor(private val storageDir: String) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    actual fun store(alias: String, bytes: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val payload = cipher.iv + cipher.doFinal(bytes)
        fileFor(alias).writeBytes(payload)
    }

    actual fun retrieve(alias: String): ByteArray? {
        val file = fileFor(alias)
        if (!file.exists()) return null
        val payload = file.readBytes()
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    actual fun remove(alias: String) {
        fileFor(alias).delete()
    }

    private fun fileFor(alias: String): File = File(storageDir, "secure_key_storage_$alias")

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
