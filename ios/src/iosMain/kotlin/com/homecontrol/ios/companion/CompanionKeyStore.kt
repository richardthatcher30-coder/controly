package com.homecontrol.ios.companion

import com.homecontrol.core.companionprotocol.CompanionCrypto
import com.homecontrol.core.crypto.SecureKeyStorage
import dev.whyoleg.cryptography.algorithms.ECDH
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val KEY_ALIAS = "companion_identity_key_pkcs8"

/**
 * Our ECDH identity for the Companion protocol -- one P-256 keypair,
 * generated once and reused for every paired Windows PC / Android Companion,
 * exactly like [com.homecontrol.ios.adb.AdbKeyStore] reuses one RSA keypair
 * for every ADB-paired TV. Mirrors the Android client's
 * `CompanionKeyStore.kt` (which persists via the Android Keystore); this one
 * persists via the same iOS Keychain [SecureKeyStorage] the ADB path
 * already uses, under a different alias so the two identities never collide.
 */
@OptIn(ExperimentalEncodingApi::class)
class CompanionKeyStore(private val secureKeyStorage: SecureKeyStorage = SecureKeyStorage("")) {

    suspend fun getOrCreatePrivateKey(): ECDH.PrivateKey {
        secureKeyStorage.retrieve(KEY_ALIAS)?.let { return CompanionCrypto.decodePrivateKey(it) }
        val keyPair = CompanionCrypto.generateKeyPair()
        secureKeyStorage.store(KEY_ALIAS, CompanionCrypto.encodePrivateKey(keyPair.privateKey))
        return keyPair.privateKey
    }

    suspend fun publicKeyBase64(): String =
        Base64.encode(CompanionCrypto.encodePublicKey(getOrCreatePrivateKey().getPublicKey()))
}
