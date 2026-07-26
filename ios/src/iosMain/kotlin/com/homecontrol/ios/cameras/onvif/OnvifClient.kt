package com.homecontrol.ios.cameras.onvif

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.providers.apple.Apple
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.posix.arc4random_buf

private const val MEDIA_WSDL = "http://www.onvif.org/ver10/media/wsdl"
private const val SCHEMA = "http://www.onvif.org/ver10/schema"

/**
 * iOS port of `feature-cameras`' `OnvifClient.kt` -- same two SOAP calls
 * (`GetProfiles` then `GetStreamUri`), same WS-Security PasswordDigest auth,
 * same `/onvif/device_service` single-endpoint assumption, same
 * localhost/127.0.0.1 host-substitution fix for budget firmware that
 * doesn't fill its own LAN address into the returned URI. Adds one thing the
 * Android version doesn't need: `GetSnapshotUri`, a standard ONVIF Media
 * service call returning a plain HTTP JPEG URL -- iOS has no RTSP video
 * decoder available (no ExoPlayer equivalent; AVFoundation dropped public
 * RTSP support years ago), so [com.homecontrol.ios.screens.cameras.CameraGridScreen]
 * polls this URL instead of decoding the RTSP stream directly. A real
 * limitation versus Android's live video, not hidden: see that screen's doc
 * comment.
 *
 * Uses Ktor's Darwin engine (already a dependency, see
 * [com.homecontrol.ios.sony.SonyApiClient]) instead of OkHttp, and
 * `cryptography-core`'s SHA-1 hasher (already proven working on this
 * toolchain via [com.homecontrol.core.companionprotocol.CompanionCrypto]'s
 * SHA-256 usage) instead of `java.security.MessageDigest`.
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class)
internal class OnvifClient(
    private val ipAddress: String,
    private val port: Int,
    private val username: String,
    private val password: String,
) {
    private val client = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 5_000
        }
    }
    private val serviceUrl = "http://$ipAddress:$port/onvif/device_service"

    /** Returns an RTSP URL with the camera's credentials embedded (`rtsp://user:pass@host:port/path`), or a failure with a human-readable reason. Not directly playable on iOS -- see [resolveSnapshotUri] -- but still resolved/stored for parity with the Android record shape and any future RTSP work. */
    suspend fun resolveStreamUri(): Result<String> {
        val profileToken = getFirstProfileToken().getOrElse { return Result.failure(it) }
        val uri = getStreamUri(profileToken).getOrElse { return Result.failure(it) }
        return Result.success(injectCredentials(useRealHost(uri)))
    }

    /** A plain HTTP JPEG snapshot URL for this camera's default media profile -- what [com.homecontrol.ios.screens.cameras.CameraGridScreen] actually polls. */
    suspend fun resolveSnapshotUri(): Result<String> {
        val profileToken = getFirstProfileToken().getOrElse { return Result.failure(it) }
        val uri = getSnapshotUri(profileToken).getOrElse { return Result.failure(it) }
        return Result.success(injectCredentials(useRealHost(uri)))
    }

    private fun useRealHost(uri: String): String =
        uri.replace("localhost", ipAddress, ignoreCase = true).replace("127.0.0.1", ipAddress)

    private suspend fun getFirstProfileToken(): Result<String> {
        val response = post("""<GetProfiles xmlns="$MEDIA_WSDL"/>""").getOrElse { return Result.failure(it) }
        val token = SimpleXml.firstElementAttribute(response, "Profiles", "token")
        return if (token.isNullOrBlank()) {
            Result.failure(IllegalStateException("Camera didn't return any media profiles"))
        } else {
            Result.success(token)
        }
    }

    private suspend fun getStreamUri(profileToken: String): Result<String> {
        val body = """
            <GetStreamUri xmlns="$MEDIA_WSDL">
                <StreamSetup>
                    <Stream xmlns="$SCHEMA">RTP-Unicast</Stream>
                    <Transport xmlns="$SCHEMA"><Protocol>RTSP</Protocol></Transport>
                </StreamSetup>
                <ProfileToken>$profileToken</ProfileToken>
            </GetStreamUri>
        """.trimIndent()
        val response = post(body).getOrElse { return Result.failure(it) }
        val uri = SimpleXml.firstElementText(response, "Uri")
        return if (uri.isNullOrBlank()) {
            Result.failure(IllegalStateException("Camera didn't return a stream URI"))
        } else {
            Result.success(uri)
        }
    }

    private suspend fun getSnapshotUri(profileToken: String): Result<String> {
        val body = """<GetSnapshotUri xmlns="$MEDIA_WSDL"><ProfileToken>$profileToken</ProfileToken></GetSnapshotUri>"""
        val response = post(body).getOrElse { return Result.failure(it) }
        val uri = SimpleXml.firstElementText(response, "Uri")
        return if (uri.isNullOrBlank()) {
            Result.failure(IllegalStateException("Camera didn't return a snapshot URI"))
        } else {
            Result.success(uri)
        }
    }

    private suspend fun post(bodyXml: String): Result<String> = runCatching {
        val response = client.post(serviceUrl) {
            contentType(ContentType("application", "soap+xml"))
            setBody(soapEnvelope(bodyXml))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val reason = SimpleXml.firstElementText(text, "Text")
            error("Camera returned HTTP ${response.status.value}${reason?.let { ": $it" } ?: ""}")
        }
        text
    }

    private fun soapEnvelope(bodyXml: String): String {
        val nonce = ByteArray(16)
        nonce.usePinned { pinned -> arc4random_buf(pinned.addressOf(0), nonce.size.convert()) }
        val created = NSISO8601DateFormatter().stringFromDate(NSDate())
        val digestInput = nonce + created.encodeToByteArray() + password.encodeToByteArray()
        val digest = CryptographyProvider.Apple.get(SHA1).hasher().hashBlocking(digestInput)

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                <s:Header>
                    <Security s:mustUnderstand="1" xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                        <UsernameToken>
                            <Username>$username</Username>
                            <Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">${Base64.encode(digest)}</Password>
                            <Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">${Base64.encode(nonce)}</Nonce>
                            <Created xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">$created</Created>
                        </UsernameToken>
                    </Security>
                </s:Header>
                <s:Body>$bodyXml</s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private fun injectCredentials(uri: String): String {
        val schemeSplit = uri.indexOf("://")
        if (schemeSplit == -1) return uri
        val scheme = uri.substring(0, schemeSplit + 3)
        val rest = uri.substring(schemeSplit + 3)
        if (rest.contains("@")) return uri // already has credentials
        return "$scheme$username:$password@$rest"
    }

    fun close() {
        client.close()
    }
}
