package com.homecontrol.ios.discovery

import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.DiscoveryProtocol
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.IFF_BROADCAST
import platform.posix.IFF_UP
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_BROADCAST
import platform.posix.SO_RCVTIMEO
import platform.posix.close
import platform.posix.errno
import platform.posix.freeifaddrs
import platform.posix.getifaddrs
import platform.posix.ifaddrs
import platform.posix.recvfrom
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.strerror
import platform.posix.timeval

private const val UDP_BROADCAST_PORT = 58600
private const val UDP_DISCOVER_MESSAGE = "HOMECONTROL_DISCOVER"
private const val UDP_RESPONSE_PREFIX = "HOMECONTROL_HERE"
private const val SCAN_TIMEOUT_SECONDS = 5L

/** Result of a [scanForWindowsPcs] run — surfaced in the UI so a real-device failure is diagnosable instead of silent. */
sealed interface UdpScanResult {
    data class Completed(val devicesFound: Int) : UdpScanResult
    data class Failed(val step: String, val errnoValue: Int, val errnoMessage: String) : UdpScanResult
}

/**
 * Re-implementation of `core:discovery`'s UDP-broadcast half of Windows PC
 * discovery for iOS. `WindowsPlugin`'s Android `canHandle()` matches only on
 * `DiscoveryProtocol.UDP_BROADCAST` (not the `_homecontrol._tcp.` mDNS
 * service also declared in `NetworkDeviceDiscoveryScanner`), so this is the
 * one that actually matters for routing a discovered device to Windows
 * pairing. `core:net-io`'s `TcpSocket` is TCP-only — no UDP support to
 * reuse — and a broadcast-probe-then-listen-for-many-replies shape doesn't
 * fit a request/response socket anyway, so this is hand-rolled directly on
 * `platform.posix`, matching `TcpSocket.ios.kt`'s own approach.
 *
 * Wire protocol (from `NetworkDeviceDiscoveryScanner.kt`): broadcast
 * "HOMECONTROL_DISCOVER" to port 58600, listen for "HOMECONTROL_HERE <name
 * with spaces> <mac>" replies for a few seconds — the MAC is always the last
 * space-separated token (never contains a space), so it anchors the split
 * rather than assuming the name is one word.
 *
 * Every syscall's return value is checked and reported via [UdpScanResult] —
 * a first real-device attempt found nothing with no error either, which
 * turned out to be because nothing was actually being checked (any silent
 * failure at socket/setsockopt/sendto would look identical to "no devices
 * on the network"). That surfaced a real bug: sending to the global
 * broadcast address 255.255.255.255 (what Android's own implementation
 * uses, and what this file originally did too) fails on iOS with
 * `EHOSTUNREACH` ("No route to host") — a well-known iOS/BSD quirk absent
 * on Android. The fix is to enumerate network interfaces via `getifaddrs`
 * and send to the active Wi-Fi interface's own subnet-directed broadcast
 * address (e.g. 192.168.1.255) instead, which iOS does route correctly.
 *
 * Blocking — call off the main thread. Pairing with a device found this way
 * isn't implemented on iOS yet (only ADB-based Android TV/Fire TV/Google TV
 * is); this surfaces Windows PCs in the Add Device list so discovery isn't
 * silently incomplete, same as the "coming soon" treatment already shown
 * for other unsupported types.
 */
@OptIn(ExperimentalForeignApi::class)
fun scanForWindowsPcs(onDeviceFound: (DiscoveredDevice) -> Unit): UdpScanResult {
    val fd = socket(AF_INET, SOCK_DGRAM, 0)
    if (fd < 0) return errnoFailure("socket")
    try {
        memScoped {
            val broadcastEnabled = alloc<IntVar>().apply { value = 1 }
            if (setsockopt(fd, SOL_SOCKET, SO_BROADCAST, broadcastEnabled.ptr, sizeOf<IntVar>().convert()) != 0) {
                return errnoFailure("setsockopt(SO_BROADCAST)")
            }

            val timeout = alloc<timeval>().apply {
                tv_sec = SCAN_TIMEOUT_SECONDS
                tv_usec = 0
            }
            if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert()) != 0) {
                return errnoFailure("setsockopt(SO_RCVTIMEO)")
            }

            // 255.255.255.255 fails with EHOSTUNREACH on iOS (confirmed on real hardware) --
            // use the Wi-Fi interface's actual subnet broadcast address instead, falling back
            // to the global address only if interface enumeration itself didn't find one.
            val broadcastAddress = findBroadcastAddress() ?: 0xFFFFFFFFu
            val destAddr = alloc<sockaddr_in>().apply {
                sin_family = AF_INET.convert()
                sin_port = hostToNetworkShort(UDP_BROADCAST_PORT)
                sin_addr.s_addr = broadcastAddress
            }
            val message = UDP_DISCOVER_MESSAGE.encodeToByteArray()
            val sent = message.usePinned { pinned ->
                sendto(
                    fd,
                    pinned.addressOf(0),
                    message.size.convert(),
                    0,
                    destAddr.ptr.reinterpret<sockaddr>(),
                    sizeOf<sockaddr_in>().convert(),
                )
            }
            if (sent < 0) return errnoFailure("sendto")

            val buffer = ByteArray(1024)
            val fromAddr = alloc<sockaddr_in>()
            val fromLen = alloc<UIntVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            var devicesFound = 0
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
                    return errnoFailure("recvfrom", err)
                }
                if (received == 0L) break
                val text = buffer.decodeToString(0, received.toInt())
                if (!text.startsWith(UDP_RESPONSE_PREFIX)) continue
                val ipAddress = formatIPv4(fromAddr.sin_addr.s_addr)
                val tokens = text.removePrefix(UDP_RESPONSE_PREFIX).trim().split(" ")
                val macAddress = tokens.lastOrNull()?.takeIf { it.isNotEmpty() }
                val name = tokens.dropLast(1).joinToString(" ").ifEmpty { ipAddress }
                devicesFound++
                onDeviceFound(
                    DiscoveredDevice(
                        discoveryId = "udp:$ipAddress",
                        name = name,
                        ipAddress = ipAddress,
                        macAddress = macAddress,
                        deviceTypeHint = DeviceType.WINDOWS_PC,
                        discoveryProtocol = DiscoveryProtocol.UDP_BROADCAST,
                    ),
                )
            }
            return UdpScanResult.Completed(devicesFound)
        }
    } finally {
        close(fd)
    }
}

/**
 * Finds the first up, broadcast-capable IPv4 interface's subnet broadcast
 * address (e.g. 192.168.1.255 for a typical home network on Wi-Fi/`en0`),
 * via `getifaddrs`. Returns null (falling back to the global broadcast
 * address) if enumeration fails or no such interface is found — loopback
 * interfaces are point-to-point and don't set `IFF_BROADCAST`, so no
 * explicit exclusion is needed for those.
 */
@OptIn(ExperimentalForeignApi::class)
private fun findBroadcastAddress(): UInt? = memScoped {
    val ifapVar = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(ifapVar.ptr) != 0) return@memScoped null
    try {
        var current = ifapVar.value
        while (current != null) {
            val ifa = current.pointed
            val flags = ifa.ifa_flags.toInt()
            val addr = ifa.ifa_addr
            val broadAddr = ifa.ifa_broadaddr
            if ((flags and IFF_UP) != 0 && (flags and IFF_BROADCAST) != 0 && addr != null && broadAddr != null) {
                if (addr.pointed.sa_family.toInt() == AF_INET) {
                    return@memScoped broadAddr.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                }
            }
            current = ifa.ifa_next
        }
        null
    } finally {
        freeifaddrs(ifapVar.value)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun errnoFailure(step: String, errnoValue: Int = errno): UdpScanResult.Failed =
    UdpScanResult.Failed(step, errnoValue, strerror(errnoValue)?.toKString() ?: "unknown error")

private fun hostToNetworkShort(value: Int): UShort {
    return (((value and 0xFF) shl 8) or ((value shr 8) and 0xFF)).toUShort()
}

private fun formatIPv4(addr: UInt): String {
    val b0 = addr and 0xFFu
    val b1 = (addr shr 8) and 0xFFu
    val b2 = (addr shr 16) and 0xFFu
    val b3 = (addr shr 24) and 0xFFu
    return "$b0.$b1.$b2.$b3"
}
