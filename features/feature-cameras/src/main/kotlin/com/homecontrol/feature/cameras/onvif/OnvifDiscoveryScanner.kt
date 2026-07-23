package com.homecontrol.feature.cameras.onvif

import android.util.Log
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val WS_DISCOVERY_MULTICAST_ADDRESS = "239.255.255.250"
private const val WS_DISCOVERY_PORT = 3702
private const val SCAN_DURATION_MS = 5_000L

/** A camera found via WS-Discovery. [xAddr] is the camera's *real* ONVIF service URL, straight from its own ProbeMatch response — no more guessing at the ONVIF port per camera. */
data class DiscoveredCamera(
    val ipAddress: String,
    val port: Int,
    val xAddr: String,
)

/**
 * ONVIF's own discovery mechanism, WS-Discovery — a distinct SOAP-over-UDP-
 * multicast protocol (port 3702), unrelated to the SSDP/mDNS scanning
 * `core:discovery` does for TVs/PCs, so this is a separate scanner rather
 * than reusing that one. Modelled closely on `NetworkDeviceDiscoveryScanner`'s
 * SSDP probe (same blocking-receive-loop-inside-runCatching shape) for
 * consistency, since the two protocols are structurally very similar.
 */
class OnvifDiscoveryScanner @Inject constructor() {

    fun scan(): Flow<DiscoveredCamera> = callbackFlow {
        val probeJob = launch(Dispatchers.IO) { probe { camera -> trySend(camera) } }
        val timeoutJob = launch { delay(SCAN_DURATION_MS); close() }

        awaitClose {
            probeJob.cancel()
            timeoutJob.cancel()
        }
    }

    private fun probe(onFound: (DiscoveredCamera) -> Unit) {
        val probeMessage = buildProbeMessage().toByteArray()

        runCatching {
            MulticastSocket().use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = SCAN_DURATION_MS.toInt()

                val group = InetAddress.getByName(WS_DISCOVERY_MULTICAST_ADDRESS)
                socket.send(DatagramPacket(probeMessage, probeMessage.size, group, WS_DISCOVERY_PORT))

                val buffer = ByteArray(8192)
                while (true) {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    parseProbeMatch(response)?.let(onFound)
                }
            }
        }
    }

    private fun buildProbeMessage(): String {
        val messageId = "uuid:${UUID.randomUUID()}"
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                        xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                        xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
                        xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
                <e:Header>
                    <w:MessageID>$messageId</w:MessageID>
                    <w:To e:mustUnderstand="1">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
                    <w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
                </e:Header>
                <e:Body>
                    <d:Probe>
                        <d:Types>dn:NetworkVideoTransmitter</d:Types>
                    </d:Probe>
                </e:Body>
            </e:Envelope>
        """.trimIndent()
    }

    private fun parseProbeMatch(response: DatagramPacket): DiscoveredCamera? = runCatching {
        val text = String(response.data, 0, response.length, Charsets.UTF_8)
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(text.byteInputStream(Charsets.UTF_8))
        // A camera can list several XAddrs (one per network interface/protocol)
        // space-separated — the first is the one it advertises as primary.
        val xAddrsText = firstElementByLocalName(doc, "XAddrs")?.textContent ?: return null
        val xAddr = xAddrsText.trim().split(" ").firstOrNull { it.isNotBlank() } ?: return null
        val uri = URI(xAddr)
        val host = uri.host ?: return null
        DiscoveredCamera(ipAddress = host, port = uri.port.takeIf { it > 0 } ?: 80, xAddr = xAddr)
    }.onFailure { error ->
        Log.d("OnvifDiscoveryScanner", "Failed to parse a WS-Discovery response: ${error.message}")
    }.getOrNull()

    private fun firstElementByLocalName(doc: Document, localName: String): Element? {
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val node = all.item(i)
            if (node is Element && node.localName == localName) return node
        }
        return null
    }
}
