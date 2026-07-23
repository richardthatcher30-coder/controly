package com.homecontrol.plugins.androidtv.adb

import android.content.Context
import com.homecontrol.core.security.KeystoreCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_FILE_NAME = "adb_keypair.enc"
private const val RSA_KEY_SIZE_BITS = 2048

/**
 * Our ADB client identity — one RSA keypair, generated once and reused for
 * every Android TV / Fire TV device we pair with, exactly like a real
 * `adbkey`/`adbkey.pub` pair. Persisted encrypted-at-rest via
 * [KeystoreCipher] rather than in Android Keystore directly, because the
 * ADB auth handshake needs a raw "NONEwithRSA" signature that Keystore-backed
 * keys don't reliably support across devices.
 */
@Singleton
class AdbKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: KeystoreCipher,
) {

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    @Synchronized
    fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }

        val file = File(context.filesDir, KEY_FILE_NAME)
        val keyPair = if (file.exists()) loadKeyPair(file) else generateKeyPair().also { saveKeyPair(it, file) }
        cachedKeyPair = keyPair
        return keyPair
    }

    private fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_SIZE_BITS) }.generateKeyPair()

    private fun saveKeyPair(keyPair: KeyPair, file: File) {
        val payload = ByteArrayPacker.pack(keyPair.private.encoded, keyPair.public.encoded)
        file.writeBytes(cipher.encrypt(payload))
    }

    private fun loadKeyPair(file: File): KeyPair {
        val (privateBytes, publicBytes) = ByteArrayPacker.unpack(cipher.decrypt(file.readBytes()))
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))
        return KeyPair(publicKey, privateKey)
    }
}
