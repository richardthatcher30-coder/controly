package com.homecontrol.plugins.windows.crypto

import android.content.Context
import com.homecontrol.core.security.KeystoreCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_FILE_NAME = "windows_companion_keypair.enc"
private const val CURVE_NAME = "secp256r1" // NIST P-256, matches the companion's ECDiffieHellman curve

/**
 * Our ECDH identity for the Windows Companion protocol — one P-256 keypair,
 * generated once and reused for every paired PC, mirroring how
 * `plugin-androidtv`'s `AdbKeyStore` reuses one RSA keypair for every TV.
 */
@Singleton
class CompanionKeyStore @Inject constructor(
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
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(CURVE_NAME)) }.generateKeyPair()

    private fun saveKeyPair(keyPair: KeyPair, file: File) {
        val payload = ByteArrayPacker.pack(keyPair.private.encoded, keyPair.public.encoded)
        file.writeBytes(cipher.encrypt(payload))
    }

    private fun loadKeyPair(file: File): KeyPair {
        val (privateBytes, publicBytes) = ByteArrayPacker.unpack(cipher.decrypt(file.readBytes()))
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))
        return KeyPair(publicKey, privateKey)
    }
}
