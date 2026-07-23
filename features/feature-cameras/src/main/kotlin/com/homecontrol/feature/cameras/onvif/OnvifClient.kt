package com.homecontrol.feature.cameras.onvif

import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element

private val SOAP_MEDIA_TYPE = "application/soap+xml; charset=utf-8".toMediaType()
private const val MEDIA_WSDL = "http://www.onvif.org/ver10/media/wsdl"
private const val SCHEMA = "http://www.onvif.org/ver10/schema"

/**
 * A minimal ONVIF SOAP client — just enough to turn a camera's IP/username/
 * password into a playable RTSP URL, not a general ONVIF SDK. Two calls:
 * `GetProfiles` (to find a media profile token) then `GetStreamUri` (to get
 * that profile's RTSP address), both WS-Security "PasswordDigest"
 * authenticated per the ONVIF core spec.
 *
 * Assumes every request goes to `/onvif/device_service` regardless of which
 * ONVIF "service" it's logically part of — most budget ONVIF cameras
 * (including generic OEM boards this SV3C is very likely built on) route
 * all SOAP calls through that one endpoint rather than exposing a separate
 * media service address, so this is the pragmatic first thing to try
 * instead of first doing a `GetCapabilities` round-trip to discover it.
 */
internal class OnvifClient(
    private val ipAddress: String,
    private val port: Int,
    private val username: String,
    private val password: String,
) {

    private val client = OkHttpClient()
    private val serviceUrl = "http://$ipAddress:$port/onvif/device_service"

    /** Returns an RTSP URL with the camera's credentials embedded (`rtsp://user:pass@host:port/path`), or a failure with a human-readable reason. */
    fun resolveStreamUri(): Result<String> {
        val profileToken = getFirstProfileToken().getOrElse { return Result.failure(it) }
        val uri = getStreamUri(profileToken).getOrElse { return Result.failure(it) }
        Log.d("OnvifClient", "Camera returned raw stream URI: $uri")
        val fixed = injectCredentials(useRealHost(uri))
        Log.d("OnvifClient", "Resolved to: ${fixed.replace(password, "***")}")
        return Result.success(fixed)
    }

    /**
     * Some budget ONVIF firmware (this SV3C camera included, confirmed via
     * its actual `GetStreamUri` response) returns a URI whose host is
     * literally `localhost`/`127.0.0.1` — presumably a template the camera
     * never substitutes its own LAN address into — which sends the phone's
     * RTSP client to connect to itself instead of the camera. Since we
     * already know the camera's real address (the one this whole request
     * was sent to), swap it in.
     */
    private fun useRealHost(uri: String): String =
        uri.replace("localhost", ipAddress, ignoreCase = true).replace("127.0.0.1", ipAddress)

    private fun getFirstProfileToken(): Result<String> {
        val body = """<GetProfiles xmlns="$MEDIA_WSDL"/>"""
        val response = post(body).getOrElse { return Result.failure(it) }
        val token = firstElementByLocalName(response, "Profiles")?.getAttribute("token")
        return if (token.isNullOrBlank()) {
            Result.failure(IllegalStateException("Camera didn't return any media profiles"))
        } else {
            Result.success(token)
        }
    }

    private fun getStreamUri(profileToken: String): Result<String> {
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
        val uri = firstElementByLocalName(response, "Uri")?.textContent
        return if (uri.isNullOrBlank()) {
            Result.failure(IllegalStateException("Camera didn't return a stream URI"))
        } else {
            Result.success(uri)
        }
    }

    private fun post(bodyXml: String): Result<Document> = runCatching {
        val request = Request.Builder()
            .url(serviceUrl)
            .post(soapEnvelope(bodyXml).toRequestBody(SOAP_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Camera returned HTTP ${response.code}${extractFaultReason(text)?.let { ": $it" } ?: ""}")
            }
            parseXml(text)
        }
    }

    private fun extractFaultReason(xml: String): String? =
        runCatching { firstElementByLocalName(parseXml(xml), "Text")?.textContent }.getOrNull()

    private fun soapEnvelope(bodyXml: String): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())
        val digest = MessageDigest.getInstance("SHA-1").digest(nonce + created.toByteArray(Charsets.UTF_8) + password.toByteArray(Charsets.UTF_8))

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                <s:Header>
                    <Security s:mustUnderstand="1" xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                        <UsernameToken>
                            <Username>$username</Username>
                            <Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">${Base64.encodeToString(digest, Base64.NO_WRAP)}</Password>
                            <Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">${Base64.encodeToString(nonce, Base64.NO_WRAP)}</Nonce>
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

    private fun parseXml(text: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(text.byteInputStream(Charsets.UTF_8))

    private fun firstElementByLocalName(doc: Document, localName: String): Element? {
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val node = all.item(i)
            if (node is Element && node.localName == localName) return node
        }
        return null
    }
}
