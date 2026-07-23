package com.homecontrol.plugins.windows.networking

import android.util.Base64
import com.homecontrol.plugins.windows.crypto.EcdhKeyAgreement
import com.homecontrol.plugins.windows.crypto.TrustOnFirstUseTrustManager
import com.homecontrol.plugins.windows.protocol.WireMessage
import com.homecontrol.plugins.windows.protocol.WireMessageType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.security.KeyPair
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal const val COMPANION_PORT = 7591
private const val COMPANION_PATH = "/companion"
private const val PAIRING_TIMEOUT_MS = 60_000L
private const val AUTH_TIMEOUT_MS = 10_000L
private const val COMMAND_TIMEOUT_MS = 5_000L

private val wireJson = Json { ignoreUnknownKeys = true }

/**
 * One WebSocket connection to a Windows Companion instance. Handles the
 * ECDH pairing handshake, the reconnect challenge-response, and command
 * dispatch — the Kotlin-side mirror of the companion's own `ClientSession`.
 */
internal class CompanionSession private constructor(
    private val client: HttpClient,
    private val session: DefaultClientWebSocketSession,
    private val keyPair: KeyPair,
    var serverPublicKeyBase64: String?,
    val certificateFingerprint: String,
) {

    suspend fun pair(deviceName: String): Boolean = withTimeout(PAIRING_TIMEOUT_MS) {
        send(WireMessage(type = WireMessageType.PAIR_REQUEST, clientPublicKey = clientPublicKeyBase64(), deviceName = deviceName))

        val challenge = receive() ?: return@withTimeout false
        if (challenge.type != WireMessageType.PAIR_CHALLENGE || challenge.serverPublicKey == null) return@withTimeout false
        serverPublicKeyBase64 = challenge.serverPublicKey

        val result = receive() ?: return@withTimeout false
        result.type == WireMessageType.PAIR_RESULT && result.success == true
    }

    suspend fun authenticate(): Boolean = withTimeout(AUTH_TIMEOUT_MS) {
        val serverKey = serverPublicKeyBase64 ?: return@withTimeout false

        send(WireMessage(type = WireMessageType.AUTH_REQUEST, clientPublicKey = clientPublicKeyBase64()))

        val challenge = receive() ?: return@withTimeout false
        if (challenge.type != WireMessageType.AUTH_CHALLENGE || challenge.nonce == null) return@withTimeout false

        val sharedSecret = EcdhKeyAgreement.computeSharedSecret(keyPair, serverKey)
        val nonce = Base64.decode(challenge.nonce, Base64.NO_WRAP)
        val proof = EcdhKeyAgreement.hmacSha256(sharedSecret, nonce)

        send(WireMessage(type = WireMessageType.AUTH_REQUEST, proofResponse = Base64.encodeToString(proof, Base64.NO_WRAP)))

        val result = receive() ?: return@withTimeout false
        result.type == WireMessageType.AUTH_RESULT && result.success == true
    }

    suspend fun sendCommand(action: String, params: JsonElement? = null): JsonElement? = withTimeout(COMMAND_TIMEOUT_MS) {
        send(WireMessage(type = WireMessageType.COMMAND, action = action, params = params))
        receive()?.data
    }

    suspend fun close() {
        runCatching { session.close() }
        client.close()
    }

    private fun clientPublicKeyBase64(): String = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    private suspend fun send(message: WireMessage) {
        session.send(Frame.Text(wireJson.encodeToString(WireMessage.serializer(), message)))
    }

    private suspend fun receive(): WireMessage? {
        val frame = session.incoming.receive()
        if (frame !is Frame.Text) return null
        return runCatching { wireJson.decodeFromString(WireMessage.serializer(), frame.readText()) }.getOrNull()
    }

    companion object {

        /**
         * Connects and completes the TLS handshake. [pinnedFingerprint] is
         * null on a first-ever pairing attempt (anything is accepted, and
         * reported back via [certificateFingerprint] for the caller to
         * persist) or the previously-pinned value on every call after that
         * (a mismatch aborts the connection).
         */
        suspend fun connect(ipAddress: String, keyPair: KeyPair, pinnedFingerprint: String?): CompanionSession {
            var observedFingerprint: String? = null
            val trustManager = TrustOnFirstUseTrustManager(pinnedFingerprint) { fingerprint -> observedFingerprint = fingerprint }

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<X509TrustManager>(trustManager), SecureRandom())
            }

            val client = HttpClient(OkHttp) {
                install(WebSockets)
                engine {
                    config {
                        sslSocketFactory(sslContext.socketFactory, trustManager)
                        hostnameVerifier { _, _ -> true }
                    }
                }
            }

            val session = client.webSocketSession {
                url {
                    protocol = URLProtocol.WSS
                    host = ipAddress
                    port = COMPANION_PORT
                    path(COMPANION_PATH)
                }
            }

            val fingerprint = observedFingerprint
            if (fingerprint == null) {
                client.close()
                error("TLS handshake completed without reporting a certificate")
            }

            return CompanionSession(client, session, keyPair, serverPublicKeyBase64 = null, certificateFingerprint = fingerprint)
        }
    }
}
