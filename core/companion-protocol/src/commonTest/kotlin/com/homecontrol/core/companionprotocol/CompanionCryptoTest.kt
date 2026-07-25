package com.homecontrol.core.companionprotocol

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * De-risking spike for this whole feature (see the "Companion protocol on iOS"
 * plan): before building a client/server on top of `cryptography-core`'s ECDH
 * support, confirm it actually produces the exact bytes `windows-companion`
 * (.NET) and the existing Android `plugin-windows` client
 * (`EcdhKeyAgreement.kt`, `javax.crypto`) would produce for the same inputs.
 * The fixed key material and expected outputs below were computed
 * independently via Node's `crypto` module (P-256 ECDH, `diffieHellman()` +
 * `createHash('sha256')` + `createHmac('sha256', ...)`) -- a third, unrelated
 * implementation of the same standard primitives -- specifically so this test
 * isn't just checking `cryptography-core` against itself.
 */
@OptIn(ExperimentalEncodingApi::class)
class CompanionCryptoTest {

    private val clientPrivatePkcs8 = Base64.decode(
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgkng74JwawMydYZfQTYGO22yi1UanN9ltzCiiSBCsPHyhRANCAASfhY/QeZeGLuZ/Im+A16hamdWE80dmtfZo7rCkPJdDUnMTbcIJUoaJ/hVfrNa3IN4+CkBVn6sGZYJj2GGX5DBD",
    )
    private val serverPublicSpki = Base64.decode(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEu+v9yu8cOoyVNfJgD5tx7HGH0ba4HDI+bcJ9N1XMXDJUKS3i09jOr/L6lnp1B2f54gNSt3gfSmiXYUCboV3Xhw==",
    )
    private val expectedSharedSecretBase64 = "krKwgGAvft6s83Jw695yUpUXDjmxBz+XbHimNoBkWwA="
    private val nonce = Base64.decode("AAECAwQFBgcICQoLDA0ODw==")
    private val expectedProofBase64 = "RDPOhZjuReOsCBbHYjSNKW2OwDrpF3hJuzEntYr17B0="

    @Test
    fun sharedSecret_matchesIndependentlyComputedTestVector() = runTest {
        val privateKey = CompanionCrypto.decodePrivateKey(clientPrivatePkcs8)
        val remotePublicKey = CompanionCrypto.decodePublicKey(serverPublicSpki)

        val sharedSecret = CompanionCrypto.sharedSecret(privateKey, remotePublicKey)

        assertEquals(expectedSharedSecretBase64, Base64.encode(sharedSecret))
    }

    @Test
    fun reconnectProof_matchesIndependentlyComputedTestVector() = runTest {
        val sharedSecret = Base64.decode(expectedSharedSecretBase64)

        val proof = CompanionCrypto.reconnectProof(sharedSecret, nonce)

        assertEquals(expectedProofBase64, Base64.encode(proof))
    }

    @Test
    fun generatedKeyPair_roundTripsThroughEncodingAndAgreesBothWays() = runTest {
        val alice = CompanionCrypto.generateKeyPair()
        val bob = CompanionCrypto.generateKeyPair()

        // Encode -> decode, so this also exercises the exact DER format used on the wire,
        // not just the in-memory key objects generateKeyPair() itself returned.
        val aliceReimportedPrivate = CompanionCrypto.decodePrivateKey(CompanionCrypto.encodePrivateKey(alice.privateKey))
        val bobReimportedPublic = CompanionCrypto.decodePublicKey(CompanionCrypto.encodePublicKey(bob.publicKey))
        val bobReimportedPrivate = CompanionCrypto.decodePrivateKey(CompanionCrypto.encodePrivateKey(bob.privateKey))
        val aliceReimportedPublic = CompanionCrypto.decodePublicKey(CompanionCrypto.encodePublicKey(alice.publicKey))

        val fromAlice = CompanionCrypto.sharedSecret(aliceReimportedPrivate, bobReimportedPublic)
        val fromBob = CompanionCrypto.sharedSecret(bobReimportedPrivate, aliceReimportedPublic)

        assertEquals(Base64.encode(fromAlice), Base64.encode(fromBob))
    }
}
