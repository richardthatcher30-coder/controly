package com.homecontrol.ios.discovery

import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.DiscoveryProtocol
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.posix.sockaddr_in

private const val SERVICE_TYPE = "_googlecast._tcp."
private const val SERVICE_DOMAIN = "local."
private const val RESOLVE_TIMEOUT_SECONDS = 5.0

/**
 * mDNS/Bonjour discovery via `NSNetServiceBrowser` — Foundation's older,
 * delegate-based Bonjour API, used instead of the newer closure-heavy
 * `Network.framework` `NWBrowser` because delegate-protocol conformance is
 * far more straightforward to express from Kotlin/Native than a
 * completion-handler-and-dispatch-queue-heavy Swift-first API.
 *
 * Browses for `_googlecast._tcp.` — Google Cast receiver advertisement,
 * which essentially every Android TV / Google TV box broadcasts, and the
 * same signal Android's own `core:discovery` module uses to spot these
 * devices (see `NSBonjourServices` already declared in Info.plist). This is
 * only used to *find and name* candidate devices; actual pairing still goes
 * through [com.homecontrol.ios.adb.AdbConnection] on port 5555 regardless of
 * how a device was discovered — mDNS here is a naming/UX convenience, not a
 * pairing transport (this app doesn't implement Google's separate TLS-based
 * Android TV Remote Service that `_androidtvremote2._tcp` actually serves).
 *
 * UNVERIFIED AGAINST REAL HARDWARE — same caveat as the rest of this iOS
 * networking code, with an extra one: the exact Kotlin signatures generated
 * for `NSNetServiceBrowserDelegateProtocol`/`NSNetServiceDelegateProtocol`
 * methods, and the `sockaddr_in` field layout, could not be confirmed
 * without a real Kotlin/Native compile — no Mac available in this
 * environment. If this file fails to compile, the delegate method
 * signatures below are the first place to check against Xcode's generated
 * Kotlin/Native Foundation headers.
 */
@OptIn(ExperimentalForeignApi::class)
class MdnsScanner {

    private var browser: NSNetServiceBrowser? = null
    private var browserDelegate: BrowserDelegate? = null
    private val resolvingServices = mutableListOf<NSNetService>()
    private val serviceDelegates = mutableListOf<ServiceDelegate>()

    fun startScan(onDeviceFound: (DiscoveredDevice) -> Unit) {
        stopScan()
        val delegate = BrowserDelegate(onServiceFound = { service -> resolve(service, onDeviceFound) })
        browserDelegate = delegate
        val newBrowser = NSNetServiceBrowser()
        newBrowser.delegate = delegate
        browser = newBrowser
        newBrowser.searchForServicesOfType(SERVICE_TYPE, inDomain = SERVICE_DOMAIN)
    }

    fun stopScan() {
        browser?.stop()
        browser = null
        browserDelegate = null
        resolvingServices.clear()
        serviceDelegates.clear()
    }

    private fun resolve(service: NSNetService, onDeviceFound: (DiscoveredDevice) -> Unit) {
        val delegate = ServiceDelegate(
            onResolved = { resolved ->
                addressOf(resolved)?.let { (ip, port) ->
                    onDeviceFound(
                        DiscoveredDevice(
                            discoveryId = "mdns:${resolved.name}:$ip",
                            name = resolved.name,
                            ipAddress = ip,
                            port = port,
                            deviceTypeHint = DeviceType.GOOGLE_TV,
                            discoveryProtocol = DiscoveryProtocol.MDNS,
                        ),
                    )
                }
                resolvingServices.remove(resolved)
            },
        )
        serviceDelegates.add(delegate)
        resolvingServices.add(service)
        service.delegate = delegate
        service.resolveWithTimeout(RESOLVE_TIMEOUT_SECONDS)
    }

    private fun addressOf(service: NSNetService): Pair<String, Int>? {
        val addresses = service.addresses ?: return null
        for (addressData in addresses) {
            val data = addressData as? NSData ?: continue
            val bytes = data.bytes ?: continue
            val sockaddrIn = bytes.reinterpret<sockaddr_in>().pointed
            if (sockaddrIn.sin_family.toInt() != AF_INET) continue
            val ip = formatIPv4(sockaddrIn.sin_addr.s_addr)
            val port = networkToHostShort(sockaddrIn.sin_port).toInt()
            return ip to port
        }
        return null
    }

    /** [addr] is already in network byte order (big-endian) — extracted byte-by-byte rather than assuming host endianness. */
    private fun formatIPv4(addr: UInt): String {
        val b0 = addr and 0xFFu
        val b1 = (addr shr 8) and 0xFFu
        val b2 = (addr shr 16) and 0xFFu
        val b3 = (addr shr 24) and 0xFFu
        return "$b0.$b1.$b2.$b3"
    }

    /**
     * Manual network-to-host byte swap for a 16-bit value, avoiding a
     * platform-specific `ntohs` import entirely (its actual package under
     * Kotlin/Native's Apple bindings wasn't `platform.posix`, and rather
     * than guess a second time, this is unambiguous: ARM64 iOS devices are
     * always little-endian, and network byte order is always big-endian, so
     * swapping the two bytes is correct regardless of which package a
     * platform `ntohs` binding would live in.
     */
    private fun networkToHostShort(value: UShort): UShort {
        val intValue = value.toInt()
        return (((intValue and 0xFF) shl 8) or ((intValue shr 8) and 0xFF)).toUShort()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class BrowserDelegate(
    private val onServiceFound: (NSNetService) -> Unit,
) : NSObject(), NSNetServiceBrowserDelegateProtocol {
    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        onServiceFound(didFindService)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ServiceDelegate(
    private val onResolved: (NSNetService) -> Unit,
) : NSObject(), NSNetServiceDelegateProtocol {
    override fun netServiceDidResolveAddress(sender: NSNetService) {
        onResolved(sender)
    }
}
