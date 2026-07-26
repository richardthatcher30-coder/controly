package com.homecontrol.ios.cameras.onvif

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSUUID
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.close
import platform.posix.errno
import platform.posix.inet_addr
import platform.posix.recvfrom
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.timeval

private const val WS_DISCOVERY_MULTICAST_ADDRESS = "239.255.255.250"
private const val WS_DISCOVERY_PORT = 3702
private const val SCAN_TIMEOUT_SECONDS = 5L

/** A camera found via WS-Discovery. [xAddr] is the camera's real ONVIF service URL, straight from its own ProbeMatch response. */
data class DiscoveredCamera(val ipAddress: String, val port: Int, val xAddr: String)

/**
 * iOS port of `feature-cameras`' `OnvifDiscoveryScanner.kt` -- ONVIF's own
 * discovery mechanism, WS-Discovery (SOAP-over-UDP-multicast, port 3702),
 * unrelated to the SSDP/mDNS scanning `core:discovery` does for TVs/PCs on
 * Android or [com.homecontrol.ios.discovery.MdnsScanner] here. Hand-rolled on
 * `platform.posix` matching [com.homecontrol.ios.discovery.scanForWindowsPcs]'s
 * own approach, since there's no multiplatform UDP socket API to reuse (see
 * that file's doc comment) -- unlike that scanner, this is a genuine
 * multicast *send* (not a subnet broadcast), so the iOS `EHOSTUNREACH`
 * global-broadcast quirk that required a subnet-address workaround there
 * doesn't apply here; multicast to 239.255.255.250 routes fine.
 *
 * Blocking -- call off the main thread.
 */
class OnvifDiscoveryScanner {

    @OptIn(ExperimentalForeignApi::class)
    fun scan(onFound: (DiscoveredCamera) -> Unit) {
        val fd = socket(AF_INET, SOCK_DGRAM, 0)
        if (fd < 0) return
        try {
            memScoped {
                val timeout = alloc<timeval>().apply {
                    tv_sec = SCAN_TIMEOUT_SECONDS
                    tv_usec = 0
                }
                if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert()) != 0) return

                val destAddr = alloc<sockaddr_in>().apply {
                    sin_family = AF_INET.convert()
                    sin_port = hostToNetworkShort(WS_DISCOVERY_PORT)
                    sin_addr.s_addr = inet_addr(WS_DISCOVERY_MULTICAST_ADDRESS)
                }
                val probe = buildProbeMessage().encodeToByteArray()
                val sent = probe.usePinned { pinned ->
                    sendto(
                        fd,
                        pinned.addressOf(0),
                        probe.size.convert(),
                        0,
                        destAddr.ptr.reinterpret<sockaddr>(),
                        sizeOf<sockaddr_in>().convert(),
                    )
                }
                if (sent < 0) return

                val buffer = ByteArray(8192)
                val fromAddr = alloc<sockaddr_in>()
                val fromLen = alloc<UIntVar>().apply { value = sizeOf<sockaddr_in>().convert() }
                while (true) {
                    val received = buffer.usePinned { pinned ->
                        recvfrom(
                            fd,
                            pinned.addressOf(0),
                            buffer.size.convert(),
                            0,
                            fromAddr.ptr.reinterpret<sockaddr>(),
                            fromLen.ptr,
                        )
                    }
                    if (received < 0) {
                        val err = errno
                        if (err == EAGAIN || err == EWOULDBLOCK) break // normal timeout -- scan window closed
                        break
                    }
                    if (received == 0L) break
                    val text = buffer.decodeToString(0, received.toInt())
                    parseProbeMatch(text)?.let(onFound)
                }
            }
        } finally {
            close(fd)
        }
    }

    private fun buildProbeMessage(): String {
        val messageId = "uuid:${NSUUID().UUIDString}"
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

    private fun parseProbeMatch(xml: String): DiscoveredCamera? {
        val xAddrsText = SimpleXml.firstElementText(xml, "XAddrs") ?: return null
        // A camera can list several XAddrs (one per network interface/protocol)
        // space-separated -- the first is the one it advertises as primary.
        val xAddr = xAddrsText.trim().split(" ").firstOrNull { it.isNotBlank() } ?: return null
        val (host, port) = parseHostPort(xAddr) ?: return null
        return DiscoveredCamera(ipAddress = host, port = port, xAddr = xAddr)
    }

    /** `java.net.URI`-free host/port split -- Kotlin/Native has no URI parser in the stdlib, and this only needs to handle a plain `http://host[:port]/path` shape real camera firmware actually returns. */
    private fun parseHostPort(url: String): Pair<String, Int>? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "").ifEmpty { return null }
        val authority = afterScheme.substringBefore("/")
        val host = authority.substringBefore(":")
        val port = authority.substringAfter(":", missingDelimiterValue = "80").toIntOrNull() ?: 80
        return host to port
    }

    private fun hostToNetworkShort(value: Int): UShort =
        (((value and 0xFF) shl 8) or ((value shr 8) and 0xFF)).toUShort()
}
