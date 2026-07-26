package com.homecontrol.core.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.DiscoveryProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

private const val SCAN_DURATION_MS = 8_000L
private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val UDP_BROADCAST_PORT = 58600
private const val UDP_DISCOVER_MESSAGE = "HOMECONTROL_DISCOVER"
private const val UDP_RESPONSE_PREFIX = "HOMECONTROL_HERE"
private const val ADB_PORT = 5555
private const val ADB_PORT_SCAN_CONNECT_TIMEOUT_MS = 400
// Gives mDNS/SSDP (which carry a real device name) a head start on claiming
// any IP this scan also finds by raw port -- see probeAdbPort's doc comment.
private const val ADB_PORT_SCAN_START_DELAY_MS = 2_000L

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

        // Every probe emits through this instead of calling trySend directly:
        // the ADB port scan (see probeAdbPort) can find the exact same IP an
        // mDNS/SSDP probe already found (real Android TVs answer both), and
        // without this a device could show up twice -- once with its real
        // name, once as a generic "possible Fire TV / Android TV".
        val seenIps = ConcurrentHashMap.newKeySet<String>()
        fun emitIfNew(device: DiscoveredDevice) {
            if (seenIps.add(device.ipAddress)) trySend(device)
        }

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discoveryListeners = MDNS_SERVICE_TYPES.map { serviceType ->
            val listener = mdnsDiscoveryListener(nsdManager, serviceType) { device -> emitIfNew(device) }
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        }

        val ssdpJob = launch(Dispatchers.IO) { probeSsdp { device -> emitIfNew(device) } }
        val udpJob = launch(Dispatchers.IO) { probeUdpBroadcast { device -> emitIfNew(device) } }
        val adbPortJob = launch(Dispatchers.IO) {
            delay(ADB_PORT_SCAN_START_DELAY_MS)
            probeAdbPort { device -> emitIfNew(device) }
        }
        val timeoutJob = launch { delay(SCAN_DURATION_MS); close() }

        awaitClose {
            discoveryListeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
            ssdpJob.cancel()
            udpJob.cancel()
            adbPortJob.cancel()
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

                // A send() to the global broadcast address fails with
                // ENETUNREACH/"No route to host" on real devices (confirmed,
                // not just an emulator quirk) whenever the phone has more
                // than one active network — e.g. Wi-Fi + mobile data both
                // on, which is the Android default — because the OS can't
                // tell which interface a broadcast should go out on and
                // picks one with no route to it. Binding the socket to the
                // Wi-Fi network specifically removes that ambiguity.
                val wifiNetwork = activeWifiNetwork()
                wifiNetwork?.let { runCatching { it.bindSocket(socket) } }

                val broadcastAddress = wifiNetwork?.let { wifiBroadcastAddress(it) } ?: InetAddress.getByName("255.255.255.255")
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

    /**
     * Fire TV in particular advertises itself over nothing this scanner
     * otherwise listens for -- confirmed against a real device: no response
     * to mDNS service queries (not even the broad "list every service type"
     * meta-query), despite having port 8009 (Cast control) open. What it
     * does have, like every Android TV/Google TV/Fire TV device, is ADB
     * debugging listening on port 5555 once enabled -- so instead of
     * listening for a broadcast that doesn't exist, this actively probes
     * every host on the phone's own /24 for that one port open. Verified
     * against a real 254-host home network: exactly the two devices that
     * actually had ADB debugging enabled (an Android TV and a Fire TV)
     * answered, nothing else did -- port 5555 isn't commonly open on other
     * consumer devices, so the false-positive rate in practice is low.
     *
     * Deliberately just a raw TCP connect-then-disconnect, never a real ADB
     * handshake: sending actual CNXN/AUTH bytes could trigger the "Allow
     * debugging?" prompt on every matching device just from opening the
     * scan screen, which would be a bad surprise during passive discovery --
     * that handshake only happens later, when the user actually taps to
     * pair. This also means the device's real name isn't known yet; it's
     * reported generically and the user names it (or it gets a real name if
     * pairing succeeds) same as any other manually-added device.
     */
    private suspend fun probeAdbPort(onFound: (DiscoveredDevice) -> Unit) = coroutineScope {
        val hosts = localSubnetHosts() ?: return@coroutineScope
        hosts.map { ip ->
            async(Dispatchers.IO) {
                if (isPortOpen(ip, ADB_PORT)) {
                    onFound(
                        DiscoveredDevice(
                            discoveryId = "adbscan:$ip",
                            name = "Amazon Firestick ($ip)",
                            ipAddress = ip,
                            port = ADB_PORT,
                            deviceTypeHint = DeviceType.FIRE_TV,
                            discoveryProtocol = DiscoveryProtocol.ADB_PORT_SCAN,
                        ),
                    )
                }
            }
        }.awaitAll()
    }

    private fun isPortOpen(ipAddress: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ipAddress, port), ADB_PORT_SCAN_CONNECT_TIMEOUT_MS)
            true
        }
    } catch (error: Exception) {
        false
    }

    /** Every host address (excluding network/broadcast) on the phone's current Wi-Fi /24 -- capped there even on a wider network, since scanning a whole /16 host-by-host isn't reasonable. */
    private fun localSubnetHosts(): List<String>? {
        val network = activeWifiNetwork() ?: return null
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkAddress = connectivityManager.getLinkProperties(network)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?: return null

        val myBytes = linkAddress.address.address
        val base = intArrayOf(myBytes[0].toInt() and 0xFF, myBytes[1].toInt() and 0xFF, myBytes[2].toInt() and 0xFF)
        val myLastOctet = myBytes[3].toInt() and 0xFF
        return (1..254)
            .filter { it != myLastOctet }
            .map { lastOctet -> "${base[0]}.${base[1]}.${base[2]}.$lastOctet" }
    }

    private fun activeWifiNetwork(): Network? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.allNetworks.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** The Wi-Fi subnet's directed broadcast address (e.g. 192.168.1.255) computed from its actual IP + prefix length, rather than the global 255.255.255.255 — more reliable, and what the socket-binding fix above is really for. */
    private fun wifiBroadcastAddress(network: Network): InetAddress? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkAddress = connectivityManager.getLinkProperties(network)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?: return null

        val addressBytes = linkAddress.address.address
        val prefixLength = linkAddress.prefixLength
        val broadcastBytes = ByteArray(4) { i ->
            val maskBits = (prefixLength - i * 8).coerceIn(0, 8)
            val maskByte = (0xFF shl (8 - maskBits)) and 0xFF
            (addressBytes[i].toInt() or maskByte.inv()).toByte()
        }
        return InetAddress.getByAddress(broadcastBytes)
    }
}
