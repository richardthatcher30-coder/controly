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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLUGIN_ID = "samsungtv"

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
                    PairingResult.Success(discovered.toPairedDevice())
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

    private fun DiscoveredDevice.toPairedDevice() = PairedDevice(
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

    override suspend fun connect(device: PairedDevice): ConnectionResult = withContext(Dispatchers.IO) {
        if (clients.containsKey(device.ipAddress)) {
            return@withContext ConnectionResult.Connected
        }

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
        keyCodeFor(key)?.let { code -> clients[device.ipAddress]?.sendKey(code) }
        Unit
    }

    override suspend fun powerOn(device: PairedDevice) = sendKey(device, RemoteKey.POWER)

    override suspend fun powerOff(device: PairedDevice) = sendKey(device, RemoteKey.POWER)

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
