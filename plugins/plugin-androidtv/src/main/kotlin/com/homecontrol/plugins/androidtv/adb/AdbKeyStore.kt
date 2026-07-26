package com.homecontrol.plugins.androidtv.adb

import android.content.Context
import android.util.Log
import com.homecontrol.core.security.KeystoreCipher
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

private const val TAG = "AdbKeyStore"
private const val KEY_FILE_NAME = "adb_keypair.enc"
private const val RSA_KEY_SIZE_BITS = 2048

/**
 * Whether [AdbKeyStore.getOrCreateKeyPair] returned the already-cached/
 * persisted identity or had to generate a brand new one this call —
 * surfaced (with [AdbKeyStore.lastKeyFingerprint]) as a direct diagnostic
 * for the "Fire TV keeps re-asking to pair" bug report: a real ADB client
 * (Google's own `adb` tool) reconnecting to the same Fire TV 6/6 times in a
 * row with the identical key never re-prompted, which rules out the TV/
 * protocol itself being flaky and points at this app's own key handling
 * specifically -- but code review alone hasn't found the exact bug, so this
 * tells the truth directly on the next real-device test instead of another
 * guess.
 */
enum class KeyOrigin { REUSED_EXISTING, FRESHLY_GENERATED }

/**
 * Our ADB client identity — one RSA keypair, generated once and reused for
 * every Android TV / Fire TV device we pair with, exactly like a real
 * `adbkey`/`adbkey.pub` pair. Persisted encrypted-at-rest via
 * [KeystoreCipher] rather than in Android Keystore directly, because the
 * ADB auth handshake needs a raw "NONEwithRSA" signature that Keystore-backed
 * keys don't reliably support across devices.
 */
class AdbKeyStore(
    private val context: Context,
    private val cipher: KeystoreCipher,
) {

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    var lastKeyOrigin: KeyOrigin? = null
        private set

    /** First 8 hex chars of SHA-256(public key bytes) -- short enough to eyeball-compare across two attempts without needing logcat. */
    var lastKeyFingerprint: String? = null
        private set

    @Synchronized
    fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let {
            lastKeyOrigin = KeyOrigin.REUSED_EXISTING
            lastKeyFingerprint = fingerprintOf(it)
            Log.d(TAG, "getOrCreateKeyPair: in-memory cache hit, fingerprint=$lastKeyFingerprint")
            return it
        }

        val file = File(context.filesDir, KEY_FILE_NAME)
        val keyPair: KeyPair
        if (file.exists()) {
            keyPair = loadKeyPair(file)
            lastKeyOrigin = KeyOrigin.REUSED_EXISTING
            Log.d(TAG, "getOrCreateKeyPair: loaded from $KEY_FILE_NAME")
        } else {
            keyPair = generateKeyPair()
            saveKeyPair(keyPair, file)
            lastKeyOrigin = KeyOrigin.FRESHLY_GENERATED
            Log.d(TAG, "getOrCreateKeyPair: no $KEY_FILE_NAME on disk, generated a new keypair")
        }
        lastKeyFingerprint = fingerprintOf(keyPair)
        Log.d(TAG, "getOrCreateKeyPair: origin=$lastKeyOrigin fingerprint=$lastKeyFingerprint")
        cachedKeyPair = keyPair
        return keyPair
    }

    private fun fingerprintOf(keyPair: KeyPair): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
        return digest.take(4).joinToString("") { byte -> "%02x".format(byte) }
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
