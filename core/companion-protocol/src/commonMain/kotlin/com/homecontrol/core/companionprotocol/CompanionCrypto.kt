package com.homecontrol.core.companionprotocol

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

private val provider = CryptographyProvider.Default
private val ecdh get() = provider.get(ECDH)

/**
 * The Companion protocol's crypto, ported from the Android `plugin-windows`
 * client's `EcdhKeyAgreement.kt` (JVM-only, `javax.crypto`) so it's usable
 * from any KMP target via `cryptography-core` -- this is the one piece of
 * this whole feature where wire-compatibility with `windows-companion/`
 * (C#/.NET) actually depends on getting the exact algorithm steps right, not
 * just "some ECDH". See each function's doc comment for the specific .NET
 * behavior being replicated.
 */
object CompanionCrypto {

    /** P-256 (secp256r1) keypair -- same curve `windows-companion` and the existing Android client use. */
    suspend fun generateKeyPair(): ECDH.KeyPair = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()

    /** PKCS8 DER, matching `CompanionKeyStore.kt`'s persisted format and what the wire's `clientPublicKey`/`serverPublicKey` fields expect on decode. */
    suspend fun encodePrivateKey(key: ECDH.PrivateKey): ByteArray = key.encodeToByteArray(EC.PrivateKey.Format.DER)

    /** X.509 SubjectPublicKeyInfo DER -- the wire format for `clientPublicKey`/`serverPublicKey` (Base64 of this). */
    suspend fun encodePublicKey(key: ECDH.PublicKey): ByteArray = key.encodeToByteArray(EC.PublicKey.Format.DER)

    suspend fun decodePrivateKey(der: ByteArray): ECDH.PrivateKey =
        ecdh.privateKeyDecoder(EC.Curve.P256).decodeFromByteArray(EC.PrivateKey.Format.DER, der)

    suspend fun decodePublicKey(der: ByteArray): ECDH.PublicKey =
        ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArray(EC.PublicKey.Format.DER, der)

    /**
     * The Companion protocol's "shared secret" -- NOT the raw ECDH output.
     * .NET's `ECDiffieHellman.DeriveKeyMaterial()`, used by `PairingManager.cs`
     * with no explicit KDF configured, defaults to `SHA-256(rawSecret)`
     * (`DeriveKeyFromHash` with no prepend/append). Skipping this hash here
     * would make this side silently derive a different "shared secret" than
     * windows-companion for the exact same keypair, and every proof-of-
     * possession check (the HMAC in [reconnectProof]) would fail.
     */
    suspend fun sharedSecret(privateKey: ECDH.PrivateKey, remotePublicKey: ECDH.PublicKey): ByteArray {
        val rawSecret = privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(remotePublicKey)
        return provider.get(SHA256).hasher().hash(rawSecret)
    }

    /** Reconnect proof-of-possession: `HMAC-SHA256(sharedSecret, nonce)`, matching `EcdhKeyAgreement.hmacSha256`/C#'s `HMACSHA256.HashData`. */
    suspend fun reconnectProof(sharedSecret: ByteArray, nonce: ByteArray): ByteArray {
        val key = provider.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, sharedSecret)
        return key.signatureGenerator().generateSignature(nonce)
    }

    /** SHA-256 hex digest of a DER-encoded certificate -- the TOFU fingerprint format `TrustOnFirstUseTrustManager.kt` already uses on Android. */
    suspend fun certificateFingerprintHex(certificateDer: ByteArray): String {
        val digest = provider.get(SHA256).hasher().hash(certificateDer)
        return digest.joinToString("") { byte -> ((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1) }
    }
}
