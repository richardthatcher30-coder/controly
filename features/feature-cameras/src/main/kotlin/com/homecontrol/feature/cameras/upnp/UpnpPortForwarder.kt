package com.homecontrol.feature.cameras.upnp

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val SSDP_TIMEOUT_MS = 3_000
private val IGD_SEARCH_TARGETS = listOf(
    "urn:schemas-upnp-org:device:InternetGatewayDevice:2",
    "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
)
private val WAN_CONNECTION_SERVICE_TYPES = listOf(
    "urn:schemas-upnp-org:service:WANIPConnection:2",
    "urn:schemas-upnp-org:service:WANIPConnection:1",
    "urn:schemas-upnp-org:service:WANPPPConnection:1",
)
private val SOAP_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()

sealed interface PortForwardOutcome {
    data class Success(val externalHost: String, val externalPort: Int) : PortForwardOutcome
    data class Failed(val reason: String) : PortForwardOutcome
}

/**
 * Best-effort automatic router port forwarding via UPnP IGD — this only
 * works while the phone is on the same LAN as the router (a router can only
 * be asked to forward a port by a device already inside its own network,
 * never from outside it), and only if UPnP is actually enabled on the
 * router (many are, some ISPs/users disable it). Always has a manual
 * fallback in the UI for when this fails, which is expected to happen on a
 * meaningful fraction of real routers — this hasn't been tested against
 * real UPnP hardware, only written to the IGD spec.
 *
 * Exposing a camera's stream directly to the internet this way is a real
 * security tradeoff (a weakly-secured camera becomes reachable by anyone
 * who finds the port), not just a technical inconvenience — the UI this
 * feeds into must make that unambiguous before a user opts in.
 */
class UpnpPortForwarder(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun forwardPort(internalIp: String, internalPort: Int, externalPort: Int): PortForwardOutcome =
        withContext(Dispatchers.IO) {
            // Without this, some devices silently drop incoming multicast
            // UDP packets (the SSDP responses this whole discovery depends
            // on) — same requirement as the existing device-discovery SSDP
            // probe in core:discovery.
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val multicastLock = wifiManager.createMulticastLock("controly-upnp").apply { acquire() }
            val gateway = try {
                discoverGateway()
            } finally {
                runCatching { multicastLock.release() }
            } ?: return@withContext PortForwardOutcome.Failed("No UPnP-capable router found on this network. Make sure you're on the same Wi-Fi as your router, and that UPnP is enabled in its settings.")

            val externalHost = getExternalIpAddress(gateway)
                ?: return@withContext PortForwardOutcome.Failed("Found your router but couldn't read its public IP address.")

            val mapped = addPortMapping(gateway, internalIp, internalPort, externalPort)
            if (mapped) {
                PortForwardOutcome.Success(externalHost, externalPort)
            } else {
                PortForwardOutcome.Failed("Your router found the request but refused to forward the port — UPnP may be disabled, or this router doesn't support it.")
            }
        }

    private data class Gateway(val controlUrl: String, val serviceType: String)

    private fun discoverGateway(): Gateway? {
        val location = probeSsdpForLocation() ?: return null
        val descriptionXml = runCatching { httpGet(location) }.getOrNull() ?: return null
        val doc = runCatching { parseXml(descriptionXml) }.getOrNull() ?: return null
        val (controlPath, serviceType) = findWanConnectionService(doc) ?: return null
        val base = runCatching { URL(location) }.getOrNull() ?: return null
        val controlUrl = runCatching { URL(base, controlPath).toString() }.getOrNull() ?: return null
        return Gateway(controlUrl, serviceType)
    }

    private fun probeSsdpForLocation(): String? {
        for (searchTarget in IGD_SEARCH_TARGETS) {
            val location = sendSsdpSearch(searchTarget)
            if (location != null) return location
        }
        return null
    }

    private fun sendSsdpSearch(searchTarget: String): String? = runCatching {
        val searchRequest = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }.toByteArray()

        MulticastSocket().use { socket ->
            socket.reuseAddress = true
            socket.soTimeout = SSDP_TIMEOUT_MS

            val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
            socket.send(DatagramPacket(searchRequest, searchRequest.size, group, SSDP_PORT))

            val buffer = ByteArray(2048)
            val deadline = System.currentTimeMillis() + SSDP_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val text = String(response.data, 0, response.length, Charsets.UTF_8)
                // LOCATION's value is itself a URL containing "http://", so pull
                // everything after just the "LOCATION:" header name rather than
                // splitting on every colon in the line.
                val locationLine = text.lineSequence().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                val location = locationLine?.substring("LOCATION:".length)?.trim()
                if (!location.isNullOrBlank()) return@runCatching location
            }
            null
        }
    }.getOrNull()

    /** Recursively searches the IGD device description XML for a WANIPConnection/WANPPPConnection service and its controlURL. */
    private fun findWanConnectionService(doc: Document): Pair<String, String>? {
        val services = doc.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val serviceType = childText(service, "serviceType") ?: continue
            if (WAN_CONNECTION_SERVICE_TYPES.any { it == serviceType }) {
                val controlUrl = childText(service, "controlURL") ?: continue
                return controlUrl to serviceType
            }
        }
        return null
    }

    private fun getExternalIpAddress(gateway: Gateway): String? {
        val response = soapCall(gateway, "GetExternalIPAddress", "") ?: return null
        return firstElementByLocalName(response, "NewExternalIPAddress")?.textContent?.takeIf { it.isNotBlank() }
    }

    private fun addPortMapping(gateway: Gateway, internalIp: String, internalPort: Int, externalPort: Int): Boolean {
        val args = """
            <NewRemoteHost></NewRemoteHost>
            <NewExternalPort>$externalPort</NewExternalPort>
            <NewProtocol>TCP</NewProtocol>
            <NewInternalPort>$internalPort</NewInternalPort>
            <NewInternalClient>$internalIp</NewInternalClient>
            <NewEnabled>1</NewEnabled>
            <NewPortMappingDescription>Controly camera</NewPortMappingDescription>
            <NewLeaseDuration>0</NewLeaseDuration>
        """.trimIndent()
        return soapCall(gateway, "AddPortMapping", args) != null
    }

    private fun soapCall(gateway: Gateway, action: String, argsXml: String): Document? = runCatching {
        val envelope = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:$action xmlns:u="${gateway.serviceType}">
                        $argsXml
                    </u:$action>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val request = Request.Builder()
            .url(gateway.controlUrl)
            .addHeader("SOAPAction", "\"${gateway.serviceType}#$action\"")
            .post(envelope.toRequestBody(SOAP_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@use null
            parseXml(text)
        }
    }.getOrNull()

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = SSDP_TIMEOUT_MS
        connection.readTimeout = SSDP_TIMEOUT_MS
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseXml(text: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(text.byteInputStream(Charsets.UTF_8))

    private fun childText(parent: Element, tagName: String): String? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName.substringAfter(':') == tagName) return node.textContent.trim()
        }
        return null
    }

    private fun firstElementByLocalName(doc: Document, localName: String): Element? {
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val node = all.item(i)
            if (node is Element && node.localName == localName) return node
        }
        return null
    }
}
