package com.homecontrol.plugins.windows.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object EcdhKeyAgreement {

    /**
     * ECDH shared secret, matching .NET's `ECDiffieHellman.DeriveKeyMaterial()`
     * — which, with no explicit KDF configured, defaults to
     * `SHA-256(rawSecret)` (`DeriveKeyFromHash` with no prepend/append), NOT
     * the raw secret itself. `KeyAgreement.generateSecret()` on this side
     * only gives the raw value, so the SHA-256 has to be applied explicitly
     * here or the two sides silently derive different "shared secrets" and
     * every proof-of-possession check fails.
     */
    fun computeSharedSecret(keyPair: KeyPair, remotePublicKeyBase64: String): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val remoteKeyBytes = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
        val remotePublicKey = keyFactory.generatePublic(X509EncodedKeySpec(remoteKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(remotePublicKey, true)
        val rawSecret = keyAgreement.generateSecret()

        return MessageDigest.getInstance("SHA-256").digest(rawSecret)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
