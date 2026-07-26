package com.homecontrol.plugins.samsungtv

import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.ConnectionResult
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.PairingFailureReason
import com.homecontrol.core.model.PairingInput
import com.homecontrol.core.model.PairingResult
import com.homecontrol.core.model.PairingStrategy
import com.homecontrol.core.model.RemoteKey
import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.plugins.samsungtv.networking.SamsungLegacyClient
import com.homecontrol.plugins.samsungtv.networking.SamsungLegacyOutcome
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLUGIN_ID = "samsungtv"
private const val WAKE_ON_LAN_PORT = 9

/**
 * Samsung Smart TVs, controlled via Samsung's older plain-TCP remote-control
 * protocol on port 55000 (see [SamsungLegacyClient]'s doc comment) — this,
 * not the modern WebSocket `samsung.remote.control` API on port 8001/8002,
 * is what a real 2015 UE60JU6000 turned out to actually speak, confirmed by
 * direct port testing against the device (8001/8002 both closed, 55000
 * open). An earlier version of this plugin targeted the modern API on the
 * assumption that 2015 was already "new-generation" Tizen; that assumption
 * was wrong for this hardware and has been replaced rather than kept as an
 * unverified alternate path.
 *
 * Pairing is [PairingStrategy.ON_SCREEN_APPROVAL]: connecting makes the TV
 * show an Allow/Deny popup identifying "HomeControl" (no PIN to type). There's
 * no token to persist — [connect] just repeats the same handshake, sending
 * the same [SamsungClientIdentity] id every time, and the TV either grants
 * it immediately (still trusted) or shows the prompt again. Whether this TV
 * actually remembers that across reconnects at all is a firmware-level
 * question this plugin can't control — some legacy Samsung sets are known
 * to re-prompt on every single connection regardless of what a client sends.
 *
 * Known gaps in this pass, not silently pretended to work:
 * - Newer Tizen TVs (~2016 onward, K-series and later) use the different
 *   modern WebSocket API instead of this one, which isn't implemented here
 *   — nobody has tested this plugin against that generation of hardware yet.
 * - Samsung's older still (2013-2014, pre-Tizen) sets, like a UE55F6320AK,
 *   are a separate lower-priority gap — not confirmed to speak this same
 *   protocol and not implemented here either.
 * - No *direct* input-source switching: `supportsInputSource` is deliberately
 *   left `false` since this protocol has no per-input list/select API like
 *   Sony's `avContent` — it can only blind-cycle one step at a time
 *   (`KEY_SOURCE`, the same as the physical remote's Source button).
 *   `supportsSourceCycle = true` exposes exactly that as a single repeatable
 *   "Source" button instead of a picker, which is the honest ceiling of
 *   what this protocol can do.
 * - No free-text keyboard input or installed-app list — Samsung exposes
 *   those through separate, more involved APIs not implemented here.
 */
class SamsungTvPlugin(
    private val clientIdentity: SamsungClientIdentity,
) : IDevicePlugin {

    override val pluginId: String = PLUGIN_ID
    override val displayName: String = "Samsung TV"
    override val supportedDeviceTypes: Set<DeviceType> = setOf(DeviceType.SAMSUNG_TV)
    override val pairingStrategy: PairingStrategy = PairingStrategy.ON_SCREEN_APPROVAL

    private val clients = ConcurrentHashMap<String, SamsungLegacyClient>()

    override fun canHandle(discovered: DiscoveredDevice): Boolean {
        val nameAndModel = "${discovered.name} ${discovered.model.orEmpty()}".lowercase()
        return nameAndModel.contains("samsung")
    }

    override suspend fun pair(discovered: DiscoveredDevice, input: PairingInput): PairingResult =
        withContext(Dispatchers.IO) {
            val client = SamsungLegacyClient(discovered.ipAddress, clientIdentity.getOrCreate())
            when (val outcome = client.connect()) {
                SamsungLegacyOutcome.Connected -> {
                    clients[discovered.ipAddress] = client
                    // SSDP (how Samsung TVs are actually discovered, unlike Windows PCs'
                    // custom broadcast which includes it directly) never reports a MAC
                    // address -- resolve it from the OS ARP cache now that a real TCP
                    // connection to the TV's IP was just made, which is what populates
                    // that cache entry in the first place. Needed for powerOn()'s
                    // Wake-on-LAN packet to have anywhere to send to.
                    val resolvedMac = discovered.macAddress ?: resolveMacFromArpCache(discovered.ipAddress)
                    PairingResult.Success(discovered.toPairedDevice(macAddress = resolvedMac))
                }
                SamsungLegacyOutcome.Denied -> {
                    client.close()
                    PairingResult.Failed(PairingFailureReason.REJECTED_BY_DEVICE)
                }
                SamsungLegacyOutcome.TimedOut -> {
                    client.close()
                    PairingResult.Failed(
                        PairingFailureReason.TIMEOUT,
                        "The TV showed an Allow/Deny prompt, but it wasn't answered in time. Check the TV screen and select Allow within 2 minutes, then try again.",
                    )
                }
                is SamsungLegacyOutcome.Failed -> {
                    client.close()
                    PairingResult.Failed(PairingFailureReason.NETWORK_ERROR, outcome.reason)
                }
            }
        }

    private fun DiscoveredDevice.toPairedDevice(macAddress: String?) = PairedDevice(
        id = "$PLUGIN_ID:$ipAddress",
        name = name,
        manufacturer = manufacturer ?: "Samsung",
        model = model ?: "Smart TV",
        ipAddress = ipAddress,
        macAddress = macAddress,
        deviceType = DeviceType.SAMSUNG_TV,
        firmwareVersion = firmwareVersion,
        capabilities = capabilities(),
        isOnline = true,
        pluginId = PLUGIN_ID,
    )

    /**
     * Reads the kernel's neighbor table (`/proc/net/arp`) for the MAC
     * associated with [ipAddress] -- readable without any special permission
     * on stock Android, the same technique long used by LAN-scanner apps.
     * Only reliable right after real IP traffic to that address (an ARP
     * entry has to actually exist), which pairing already guarantees by the
     * time this is called.
     */
    private fun resolveMacFromArpCache(ipAddress: String): String? = runCatching {
        File("/proc/net/arp").readLines()
            .drop(1) // header row: "IP address  HW type  Flags  HW address  Mask  Device"
            .mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"))
                if (fields.size >= 4 && fields[0] == ipAddress) fields[3] else null
            }
            .firstOrNull()
            ?.takeUnless { it == "00:00:00:00:00:00" }
    }.getOrNull()

    override suspend fun connect(device: PairedDevice): ConnectionResult = withContext(Dispatchers.IO) {
        // Always rebuild rather than trusting a cached client's mere
        // presence in the map — a locked/backgrounded phone can silently
        // kill the underlying socket with no local signal that it died.
        // Unlike Android TV's ADB handshake (which had a real regression
        // from unconditional rebuilding on real hardware), repeating this
        // handshake against a TV that already trusts this client is
        // documented above as safe — the TV either re-grants silently or
        // re-prompts, which some sets already do on every connection
        // regardless, per the class doc.
        clients.remove(device.ipAddress)?.close()

        val client = SamsungLegacyClient(device.ipAddress, clientIdentity.getOrCreate())
        when (val outcome = client.connect()) {
            SamsungLegacyOutcome.Connected -> {
                clients[device.ipAddress] = client
                ConnectionResult.Connected
            }
            // The TV no longer recognizes this app (reset, or its trusted
            // list was cleared) — a fresh on-screen approval is needed.
            SamsungLegacyOutcome.Denied -> {
                client.close()
                ConnectionResult.PairingRequired
            }
            SamsungLegacyOutcome.TimedOut -> {
                client.close()
                ConnectionResult.Failed("Timed out connecting to ${device.name}")
            }
            is SamsungLegacyOutcome.Failed -> {
                client.close()
                ConnectionResult.Failed(outcome.reason)
            }
        }
    }

    override suspend fun disconnect(device: PairedDevice) {
        clients.remove(device.ipAddress)?.close()
    }

    override suspend fun getCapabilities(device: PairedDevice): DeviceCapabilities = capabilities()

    override suspend fun sendKey(device: PairedDevice, key: RemoteKey) = withContext(Dispatchers.IO) {
        val code = keyCodeFor(key) ?: return@withContext
        val client = clients[device.ipAddress] ?: error("Not connected to ${device.name} — call connect() first")
        try {
            client.sendKey(code)
        } catch (error: Exception) {
            // The connection died mid-session — drop it so the next
            // connect() call rebuilds instead of repeatedly hitting the
            // same broken socket forever.
            clients.remove(device.ipAddress, client)
            client.close()
            throw error
        }
    }

    /**
     * A fully-off legacy Samsung set has no network stack listening on port
     * 55000 for [sendKey] to reach at all -- Wake-on-LAN is the only way to
     * turn one on remotely. The TV has to have "Anynet+ (HDMI-CEC)" enabled
     * in its own settings for this to actually work: that's the setting
     * that also gates Samsung's "Power on with Mobile" Wi-Fi standby mode,
     * which is what keeps its network interface listening for a WOL packet
     * while otherwise fully off. This is unrelated to real HDMI-CEC
     * signaling (which needs an actual HDMI cable a phone app has no way to
     * drive) -- it's just the TV-side name for the setting that has to be on.
     */
    override suspend fun powerOn(device: PairedDevice) {
        val mac = device.macAddress ?: return
        withContext(Dispatchers.IO) { sendWakeOnLanPacket(mac) }
    }

    override suspend fun powerOff(device: PairedDevice) = sendKey(device, RemoteKey.POWER)

    private fun sendWakeOnLanPacket(macAddress: String) {
        val macBytes = macAddress.split(":", "-").map { it.toInt(16).toByte() }.toByteArray()
        if (macBytes.size != 6) return

        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), WAKE_ON_LAN_PORT))
        }
    }

    override suspend fun volumeUp(device: PairedDevice) = sendKey(device, RemoteKey.VOLUME_UP)

    override suspend fun volumeDown(device: PairedDevice) = sendKey(device, RemoteKey.VOLUME_DOWN)

    override suspend fun mute(device: PairedDevice) = sendKey(device, RemoteKey.MUTE)

    override suspend fun sendText(device: PairedDevice, text: String) {
        // No free-text keyboard input wired up yet — supportsKeyboard is
        // false, so conforming UI never calls this.
    }

    override suspend fun launchApp(device: PairedDevice, appId: String) {
        // supportsApps is false — Samsung's app list/launch API is a
        // separate, more involved thing not implemented in this first pass.
    }

    override suspend fun getInstalledApps(device: PairedDevice): List<AppInfo> = emptyList()

    private fun capabilities() = DeviceCapabilities(
        supportsPower = true,
        supportsVolume = true,
        supportsChannels = true,
        supportsInputSource = false,
        supportsSourceCycle = true,
        supportsSmartHub = true,
        supportsWakeOnLan = true,
    )

    private fun keyCodeFor(key: RemoteKey): String? = when (key) {
        RemoteKey.DPAD_UP -> "KEY_UP"
        RemoteKey.DPAD_DOWN -> "KEY_DOWN"
        RemoteKey.DPAD_LEFT -> "KEY_LEFT"
        RemoteKey.DPAD_RIGHT -> "KEY_RIGHT"
        RemoteKey.DPAD_CENTER -> "KEY_ENTER"
        RemoteKey.BACK -> "KEY_RETURN"
        RemoteKey.HOME -> "KEY_HOME"
        RemoteKey.MENU -> "KEY_MENU"
        RemoteKey.VOLUME_UP -> "KEY_VOLUP"
        RemoteKey.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteKey.MUTE -> "KEY_MUTE"
        // "KEY_POWER" is the 2016+ naming; this legacy (pre-2016) protocol uses "KEY_POWEROFF".
        RemoteKey.POWER -> "KEY_POWEROFF"
        RemoteKey.CHANNEL_UP -> "KEY_CHUP"
        RemoteKey.CHANNEL_DOWN -> "KEY_CHDOWN"
        RemoteKey.INPUT_SOURCE -> "KEY_SOURCE"
        RemoteKey.SMART_HUB -> "KEY_CONTENTS"
        else -> null
    }
}
