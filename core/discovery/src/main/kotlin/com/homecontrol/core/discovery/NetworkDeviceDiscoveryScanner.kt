package com.homecontrol.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.DiscoveryProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket

private const val SCAN_DURATION_MS = 8_000L
private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val UDP_BROADCAST_PORT = 58600
private const val UDP_DISCOVER_MESSAGE = "HOMECONTROL_DISCOVER"
private const val UDP_RESPONSE_PREFIX = "HOMECONTROL_HERE"

private val MDNS_SERVICE_TYPES = listOf(
    "_androidtvremote2._tcp.",
    "_googlecast._tcp.",
    "_homecontrol._tcp.",
)

/**
 * Real mDNS (NsdManager) + SSDP (UDP multicast M-SEARCH) + UDP broadcast
 * scanning, merged into one [DiscoveredDevice] stream over a fixed window.
 * A [WifiManager.MulticastLock] is held for the duration — without it, many
 * devices silently drop the multicast traffic mDNS/SSDP depend on.
 */
class NetworkDeviceDiscoveryScanner(
    private val context: Context,
) : DeviceDiscoveryScanner {

    override fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("homecontrol-discovery").apply {
            setReferenceCounted(true)
            acquire()
        }

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discoveryListeners = MDNS_SERVICE_TYPES.map { serviceType ->
            val listener = mdnsDiscoveryListener(nsdManager, serviceType) { device -> trySend(device) }
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        }

        val ssdpJob = launch(Dispatchers.IO) { probeSsdp { device -> trySend(device) } }
        val udpJob = launch(Dispatchers.IO) { probeUdpBroadcast { device -> trySend(device) } }
        val timeoutJob = launch { delay(SCAN_DURATION_MS); close() }

        awaitClose {
            discoveryListeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
            ssdpJob.cancel()
            udpJob.cancel()
            timeoutJob.cancel()
            runCatching { multicastLock.release() }
        }
    }

    private fun mdnsDiscoveryListener(
        nsdManager: NsdManager,
        serviceType: String,
        onFound: (DiscoveredDevice) -> Unit,
    ): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {

        override fun onDiscoveryStarted(regType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, mdnsResolveListener(serviceType, onFound))
        }
    }

    private fun mdnsResolveListener(
        serviceType: String,
        onFound: (DiscoveredDevice) -> Unit,
    ): NsdManager.ResolveListener = object : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host ?: return
            onFound(
                DiscoveredDevice(
                    discoveryId = "mdns:${serviceInfo.serviceName}",
                    name = serviceInfo.serviceName,
                    ipAddress = host.hostAddress.orEmpty(),
                    port = serviceInfo.port,
                    discoveryProtocol = DiscoveryProtocol.MDNS,
                    serviceRecords = mapOf("serviceType" to serviceType),
                ),
            )
        }
    }

    private fun probeSsdp(onFound: (DiscoveredDevice) -> Unit) {
        val searchRequest = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 3\r\n")
            append("ST: ssdp:all\r\n")
            append("\r\n")
        }.toByteArray()

        runCatching {
            MulticastSocket().use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = SCAN_DURATION_MS.toInt()

                val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                socket.send(DatagramPacket(searchRequest, searchRequest.size, group, SSDP_PORT))

                val buffer = ByteArray(2048)
                while (true) {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    onFound(parseSsdpResponse(response))
                }
            }
        }
    }

    private fun parseSsdpResponse(response: DatagramPacket): DiscoveredDevice {
        val text = String(response.data, 0, response.length, Charsets.UTF_8)
        val ipAddress = response.address.hostAddress.orEmpty()
        val headers = text.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val separatorIndex = line.indexOf(':')
                if (separatorIndex <= 0) {
                    null
                } else {
                    line.substring(0, separatorIndex).trim().uppercase() to line.substring(separatorIndex + 1).trim()
                }
            }
            .toMap()

        return DiscoveredDevice(
            // Keyed by IP, not USN: a single device (a router, a TV) answers
            // an M-SEARCH once per UPnP service it exposes, each with a
            // different USN — keying on USN would show the same physical
            // device as five or six near-identical entries in the list.
            discoveryId = "ssdp:$ipAddress",
            name = headers["SERVER"] ?: ipAddress,
            ipAddress = ipAddress,
            discoveryProtocol = DiscoveryProtocol.SSDP,
            serviceRecords = headers,
        )
    }

    private fun probeUdpBroadcast(onFound: (DiscoveredDevice) -> Unit) {
        val message = UDP_DISCOVER_MESSAGE.toByteArray()

        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = SCAN_DURATION_MS.toInt()

                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(message, message.size, broadcastAddress, UDP_BROADCAST_PORT))

                val buffer = ByteArray(1024)
                while (true) {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val text = String(response.data, 0, response.length, Charsets.UTF_8)
                    if (text.startsWith(UDP_RESPONSE_PREFIX)) {
                        val ipAddress = response.address.hostAddress.orEmpty()
                        // Response shape: "HOMECONTROL_HERE <display name with spaces> <mac>"
                        // — the MAC (never contains a space) is always the last
                        // token, so it anchors the split rather than assuming
                        // the name is a single word.
                        val tokens = text.removePrefix(UDP_RESPONSE_PREFIX).trim().split(" ")
                        val macAddress = tokens.lastOrNull()?.takeIf { it.isNotEmpty() }
                        val name = tokens.dropLast(1).joinToString(" ").ifEmpty { ipAddress }
                        onFound(
                            DiscoveredDevice(
                                discoveryId = "udp:$ipAddress",
                                name = name,
                                ipAddress = ipAddress,
                                macAddress = macAddress,
                                discoveryProtocol = DiscoveryProtocol.UDP_BROADCAST,
                            ),
                        )
                    }
                }
            }
        }
    }
}
