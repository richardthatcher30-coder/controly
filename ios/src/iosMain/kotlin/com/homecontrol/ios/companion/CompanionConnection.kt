package com.homecontrol.ios.companion

import com.homecontrol.core.companionprotocol.CompanionCrypto
import com.homecontrol.core.companionprotocol.WireMessage
import com.homecontrol.core.companionprotocol.WireMessageType
import com.homecontrol.core.crypto.toByteArray
import com.homecontrol.ios.storage.CompanionDeviceStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustGetCertificateAtIndex

private const val COMPANION_PORT = 7591
private const val COMPANION_PATH = "/companion"
private const val PAIRING_TIMEOUT_MS = 120_000L
private const val AUTH_TIMEOUT_MS = 10_000L
private const val COMMAND_TIMEOUT_MS = 5_000L
private const val DEVICE_NAME = "Controly iOS"

class CompanionPairingRejectedException(message: String) : Exception(message)
class CompanionHandshakeException(message: String) : Exception(message)

/**
 * One WebSocket connection to a Companion protocol server (a Windows PC
 * running `windows-companion`, or the equivalent new Android Companion app).
 * Wire-compatible port of the existing Android client's `CompanionSession.kt`
 * -- same ECDH pairing handshake, same reconnect challenge-response, same
 * command dispatch -- using Ktor's Darwin engine (wraps `NSURLSession`)
 * instead of OkHttp, and [CompanionCrypto] (`cryptography-core`) instead of
 * `javax.crypto`, since neither is available on Kotlin/Native.
 *
 * Blocking-shaped like [com.homecontrol.ios.adb.AdbConnection] at the call
 * site (`pair`/`connect`/`sendCommand` are all suspend, called from
 * `Dispatchers.Default`), even though the underlying transport is
 * inherently async (Ktor/coroutines) unlike ADB's raw blocking sockets.
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class, UnsafeNumber::class)
class CompanionConnection(
    private val keyStore: CompanionKeyStore = CompanionKeyStore(),
    private val deviceStore: CompanionDeviceStore = CompanionDeviceStore(),
) {
    private val wireJson = Json { ignoreUnknownKeys = true }
    private var client: HttpClient? = null
    private var session: DefaultClientWebSocketSession? = null
    private var serverPublicKeyBase64: String? = null

    /**
     * First-ever pairing: connects (accepting whatever cert the server
     * presents, TOFU), sends [WireMessageType.PAIR_REQUEST], and blocks until
     * the server's on-screen Allow/Deny prompt is answered -- mirroring
     * [com.homecontrol.ios.adb.AdbConnection.pair]'s "check the TV's screen"
     * shape, just for a PC's screen instead. Persists the server's public key
     * and the pinned cert fingerprint to [CompanionDeviceStore] on success.
     */
    suspend fun pair(ipAddress: String): Unit = withTimeout(PAIRING_TIMEOUT_MS) {
        val observedFingerprint = openSocket(ipAddress, pinnedFingerprint = null)
        try {
            send(WireMessage(type = WireMessageType.PAIR_REQUEST, clientPublicKey = keyStore.publicKeyBase64(), deviceName = DEVICE_NAME))

            val challenge = receive() ?: throw CompanionHandshakeException("No response to pairing request")
            val challengeServerPublicKey = challenge.serverPublicKey
            if (challenge.type != WireMessageType.PAIR_CHALLENGE || challengeServerPublicKey == null) {
                throw CompanionHandshakeException("Unexpected response: ${challenge.type}")
            }
            serverPublicKeyBase64 = challengeServerPublicKey

            val result = receive() ?: throw CompanionHandshakeException("No pairing result received")
            if (result.type != WireMessageType.PAIR_RESULT || result.success != true) {
                throw CompanionPairingRejectedException("Pairing was declined on the PC")
            }

            deviceStore.save(
                CompanionDeviceStore.Record(
                    ipAddress = ipAddress,
                    serverPublicKeyBase64 = challengeServerPublicKey,
                    certificateFingerprint = observedFingerprint,
                ),
            )
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    /**
     * Fast reconnect to an already-paired device -- no UI wait, uses the
     * persisted server public key + pinned cert fingerprint from
     * [CompanionDeviceStore] and the HMAC challenge-response instead.
     */
    suspend fun connect(ipAddress: String): Unit = withTimeout(AUTH_TIMEOUT_MS) {
        val record = deviceStore.find(ipAddress) ?: throw CompanionHandshakeException("$ipAddress was never paired")
        openSocket(ipAddress, pinnedFingerprint = record.certificateFingerprint)
        serverPublicKeyBase64 = record.serverPublicKeyBase64
        try {
            send(WireMessage(type = WireMessageType.AUTH_REQUEST, clientPublicKey = keyStore.publicKeyBase64()))

            val challenge = receive() ?: throw CompanionHandshakeException("No response to auth request")
            val challengeNonce = challenge.nonce
            if (challenge.type != WireMessageType.AUTH_CHALLENGE || challengeNonce == null) {
                throw CompanionHandshakeException("Unexpected response: ${challenge.type}")
            }

            val sharedSecret = CompanionCrypto.sharedSecret(keyStore.getOrCreatePrivateKey(), CompanionCrypto.decodePublicKey(Base64.decode(record.serverPublicKeyBase64)))
            val proof = CompanionCrypto.reconnectProof(sharedSecret, Base64.decode(challengeNonce))
            send(WireMessage(type = WireMessageType.AUTH_REQUEST, proofResponse = Base64.encode(proof)))

            val result = receive() ?: throw CompanionHandshakeException("No auth result received")
            if (result.type != WireMessageType.AUTH_RESULT || result.success != true) {
                throw CompanionHandshakeException("PC rejected this identity -- try pairing again")
            }
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    suspend fun sendCommand(action: String, params: JsonElement? = null): JsonElement? = withTimeout(COMMAND_TIMEOUT_MS) {
        send(WireMessage(type = WireMessageType.COMMAND, action = action, params = params))
        receive()?.data
    }

    suspend fun disconnect() {
        runCatching { session?.close() }
        client?.close()
        session = null
        client = null
    }

    /** Opens the WSS connection and TLS handshake, returning the server certificate's observed fingerprint. */
    private suspend fun openSocket(ipAddress: String, pinnedFingerprint: String?): String {
        var observedFingerprint: String? = null

        val newClient = HttpClient(Darwin) {
            install(WebSockets)
            engine {
                handleChallenge { _, _, challenge, completionHandler ->
                    if (challenge.protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
                        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                        return@handleChallenge
                    }
                    val trust = challenge.protectionSpace.serverTrust()
                    val certificate = trust?.let { SecTrustGetCertificateAtIndex(it, 0) }
                    val certificateData = certificate?.let { SecCertificateCopyData(it) }
                    val certificateDer = certificateData?.let { (CFBridgingRelease(it) as NSData).toByteArray() }

                    if (trust == null || certificateDer == null) {
                        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                        return@handleChallenge
                    }

                    val fingerprint = CompanionCrypto.certificateFingerprintHexBlocking(certificateDer)
                    // Trust-on-first-use: pinnedFingerprint is null only on a brand-new pairing
                    // attempt (see pair()'s call to openSocket) -- anything is accepted there,
                    // and reported back via observedFingerprint for the caller to persist.
                    // Every later connect() passes the previously-pinned value; a mismatch here
                    // means the PC's certificate changed since pairing -- refuse it rather than
                    // silently trusting a possible impersonator.
                    if (pinnedFingerprint != null && !pinnedFingerprint.equals(fingerprint, ignoreCase = true)) {
                        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                        return@handleChallenge
                    }

                    observedFingerprint = fingerprint
                    completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
                }
            }
        }

        val newSession = newClient.webSocketSession {
            url {
                protocol = URLProtocol.WSS
                host = ipAddress
                port = COMPANION_PORT
                path(COMPANION_PATH)
            }
        }

        val fingerprint = observedFingerprint
        if (fingerprint == null) {
            newClient.close()
            throw CompanionHandshakeException("TLS handshake completed without reporting a certificate")
        }

        client = newClient
        session = newSession
        return fingerprint
    }

    private suspend fun send(message: WireMessage) {
        val activeSession = session ?: throw CompanionHandshakeException("Not connected")
        activeSession.send(Frame.Text(wireJson.encodeToString(WireMessage.serializer(), message)))
    }

    private suspend fun receive(): WireMessage? {
        val activeSession = session ?: return null
        val frame = activeSession.incoming.receive()
        if (frame !is Frame.Text) return null
        return runCatching { wireJson.decodeFromString(WireMessage.serializer(), frame.readText()) }.getOrNull()
    }
}
