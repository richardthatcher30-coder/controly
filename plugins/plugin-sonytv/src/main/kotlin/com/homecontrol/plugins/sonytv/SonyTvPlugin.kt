package com.homecontrol.plugins.sonytv

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
import com.homecontrol.plugins.sonytv.networking.RegistrationOutcome
import com.homecontrol.plugins.sonytv.networking.SonyApiClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLUGIN_ID = "sonytv"
private val SONY_MODEL_PREFIXES = listOf("kd-", "kdl-", "xr-", "xbr-")

private data class PendingRegistration(val client: SonyApiClient, val clientId: String)

/**
 * Sony Bravia TVs, controlled via Sony's own local "ScalarWebAPI" + IRCC-IP
 * protocol — not ADB. Confirmed on real hardware that ADB's on-screen
 * approval dialog is unreliable to get on fresh reconnects (sometimes just
 * never displays), while this plugin's PIN-based pairing and TV-hardware
 * IRCC commands (source, channel, power) work regardless of whether the
 * Android TV layer is even active — that's what makes this the primary
 * plugin for Sony hardware, not a fallback.
 *
 * Pairing is [PairingStrategy.CODE_VERIFICATION]: the first `actRegister`
 * call makes the TV show a PIN on-screen (no TV-side setup needed beyond
 * having IP Control available, which is on by default); the user types
 * that PIN back into the app, which becomes the second `actRegister` call.
 * Reconnecting after an app restart replays `actRegister` with the same
 * `clientid` (persisted via [SonyDeviceStore]) — Sony's trust model is
 * keyed by that id, so the TV recognizes it immediately with no new PIN.
 *
 * Input switching (HDMI 1/2/3/4, etc.) goes through [getSources]/[selectSource]
 * — Sony's `avContent` service (`getCurrentExternalInputsStatus` /
 * `setPlayContent`), which switches straight to a specific input by its real
 * `uri`. That's a different, more capable path than the IRCC "Input" button
 * (`RemoteKey.INPUT_SOURCE`, still wired for completeness) which only cycles
 * one step at a time and can't jump straight to a specific input.
 *
 * Known gaps in this first pass, not silently pretended to work:
 * - App launching needs the exact internal URI `getApplicationList`
 *   returns for a given app, not a friendly name — `supportsApps` is left
 *   false rather than exposing custom app buttons that would mostly fail.
 */
@Singleton
class SonyTvPlugin @Inject constructor(
    private val deviceStore: SonyDeviceStore,
) : IDevicePlugin {

    override val pluginId: String = PLUGIN_ID
    override val displayName: String = "Sony TV"
    override val supportedDeviceTypes: Set<DeviceType> = setOf(DeviceType.SONY_TV)
    override val pairingStrategy: PairingStrategy = PairingStrategy.CODE_VERIFICATION

    private val clients = ConcurrentHashMap<String, SonyApiClient>()
    private val pendingRegistrations = ConcurrentHashMap<String, PendingRegistration>()

    // The TV's own verified IRCC codes, keyed by Sony's command names —
    // queried once per connection via getRemoteControllerInfo rather than
    // trusted from a hardcoded "widely published" table, since that table
    // turned out to only partially match this exact model in practice.
    private val remoteCodes = ConcurrentHashMap<String, Map<String, String>>()

    override fun canHandle(discovered: DiscoveredDevice): Boolean {
        val nameAndModel = "${discovered.name} ${discovered.model.orEmpty()}".lowercase()

        // Claims every Sony-branded device, even ones that also look like a
        // plain Android TV — plugin-androidtv explicitly defers to this
        // plugin for Sony hardware (see its own canHandle()).
        return nameAndModel.contains("sony") ||
            nameAndModel.contains("bravia") ||
            SONY_MODEL_PREFIXES.any { nameAndModel.contains(it) }
    }

    override suspend fun pair(discovered: DiscoveredDevice, input: PairingInput): PairingResult =
        withContext(Dispatchers.IO) {
            when (input) {
                PairingInput.None -> beginPairing(discovered)
                is PairingInput.Code -> completePairing(discovered, input.code)
            }
        }

    private fun beginPairing(discovered: DiscoveredDevice): PairingResult {
        val client = SonyApiClient(discovered.ipAddress)
        val clientId = UUID.randomUUID().toString()

        return when (val outcome = client.beginRegistration(clientId)) {
            RegistrationOutcome.Registered -> {
                // Some TVs skip the PIN challenge entirely if IP Control is
                // already set to unauthenticated "Normal" mode.
                onRegistered(discovered.ipAddress, clientId, client)
                PairingResult.Success(discovered.toPairedDevice())
            }
            RegistrationOutcome.PinRequired -> {
                pendingRegistrations[discovered.ipAddress] = PendingRegistration(client, clientId)
                PairingResult.AwaitingApproval("Enter the code shown on your TV screen")
            }
            is RegistrationOutcome.Failed -> PairingResult.Failed(PairingFailureReason.NETWORK_ERROR, outcome.reason)
        }
    }

    private fun completePairing(discovered: DiscoveredDevice, code: String): PairingResult {
        val pending = pendingRegistrations.remove(discovered.ipAddress)
            ?: return PairingResult.Failed(PairingFailureReason.NETWORK_ERROR)

        return when (val outcome = pending.client.completeRegistration(pending.clientId, code)) {
            RegistrationOutcome.Registered -> {
                onRegistered(discovered.ipAddress, pending.clientId, pending.client)
                PairingResult.Success(discovered.toPairedDevice())
            }
            RegistrationOutcome.PinRequired -> PairingResult.Failed(PairingFailureReason.INVALID_CODE)
            is RegistrationOutcome.Failed -> PairingResult.Failed(PairingFailureReason.NETWORK_ERROR, outcome.reason)
        }
    }

    private fun onRegistered(ipAddress: String, clientId: String, client: SonyApiClient) {
        clients[ipAddress] = client
        deviceStore.save(SonyDeviceRecord(ipAddress, clientId))
        remoteCodes[ipAddress] = client.getRemoteControllerCodes().orEmpty()
    }

    private fun DiscoveredDevice.toPairedDevice() = PairedDevice(
        id = "$PLUGIN_ID:$ipAddress",
        name = name,
        manufacturer = manufacturer ?: "Sony",
        model = model ?: "Bravia",
        ipAddress = ipAddress,
        macAddress = macAddress,
        deviceType = DeviceType.SONY_TV,
        firmwareVersion = firmwareVersion,
        capabilities = capabilities(),
        isOnline = true,
        pluginId = PLUGIN_ID,
    )

    override suspend fun connect(device: PairedDevice): ConnectionResult = withContext(Dispatchers.IO) {
        if (clients.containsKey(device.ipAddress)) {
            return@withContext ConnectionResult.Connected
        }

        val record = deviceStore.find(device.ipAddress) ?: return@withContext ConnectionResult.PairingRequired
        val client = SonyApiClient(device.ipAddress)
        when (client.beginRegistration(record.clientId)) {
            RegistrationOutcome.Registered -> {
                onRegistered(device.ipAddress, record.clientId, client)
                ConnectionResult.Connected
            }
            // The TV no longer recognizes this clientid (e.g. it was reset
            // or its trusted-device list was cleared) — a fresh PIN pairing
            // is the only way forward, same as the Windows plugin's
            // PairingRequired for an invalidated certificate.
            RegistrationOutcome.PinRequired -> ConnectionResult.PairingRequired
            is RegistrationOutcome.Failed -> ConnectionResult.Failed("Couldn't reconnect to ${device.name}")
        }
    }

    override suspend fun disconnect(device: PairedDevice) {
        clients.remove(device.ipAddress)?.close()
        remoteCodes.remove(device.ipAddress)
    }

    override suspend fun getCapabilities(device: PairedDevice): DeviceCapabilities = capabilities()

    override suspend fun sendKey(device: PairedDevice, key: RemoteKey) = withContext(Dispatchers.IO) {
        irccCodeFor(device.ipAddress, key)?.let { code -> clients[device.ipAddress]?.sendIrcc(code) }
        Unit
    }

    override suspend fun powerOn(device: PairedDevice) = sendKey(device, RemoteKey.POWER)

    override suspend fun powerOff(device: PairedDevice) = sendKey(device, RemoteKey.POWER)

    override suspend fun volumeUp(device: PairedDevice) = sendKey(device, RemoteKey.VOLUME_UP)

    override suspend fun volumeDown(device: PairedDevice) = sendKey(device, RemoteKey.VOLUME_DOWN)

    override suspend fun mute(device: PairedDevice) = sendKey(device, RemoteKey.MUTE)

    override suspend fun sendText(device: PairedDevice, text: String) {
        // No free-text keyboard input exposed by this TV's local API;
        // supportsKeyboard is false, so conforming UI never calls this.
    }

    override suspend fun launchApp(device: PairedDevice, appId: String) {
        // supportsApps is false — see the class doc for why (needs the
        // exact app URI from getApplicationList, not a friendly name).
    }

    override suspend fun getInstalledApps(device: PairedDevice): List<AppInfo> = emptyList()

    override suspend fun getSources(device: PairedDevice): List<AppInfo> = withContext(Dispatchers.IO) {
        val inputs = clients[device.ipAddress]?.getExternalInputs() ?: return@withContext emptyList()
        inputs.map { (uri, title) -> AppInfo(appId = uri, displayName = title) }
    }

    override suspend fun selectSource(device: PairedDevice, sourceId: String) = withContext(Dispatchers.IO) {
        clients[device.ipAddress]?.setActiveInput(sourceId)
        Unit
    }

    private fun capabilities() = DeviceCapabilities(
        supportsPower = true,
        supportsVolume = true,
        supportsChannels = true,
        supportsInputSource = true,
    )

    /** This TV's own verified code if we have one cached, otherwise the widely-published standard Bravia code as a best-effort fallback. */
    private fun irccCodeFor(ipAddress: String, key: RemoteKey): String? {
        val sonyName = sonyNameFor(key) ?: return null
        return remoteCodes[ipAddress]?.get(sonyName) ?: fallbackIrccCodeFor(key)
    }

    private fun sonyNameFor(key: RemoteKey): String? = when (key) {
        RemoteKey.DPAD_UP -> "Up"
        RemoteKey.DPAD_DOWN -> "Down"
        RemoteKey.DPAD_LEFT -> "Left"
        RemoteKey.DPAD_RIGHT -> "Right"
        RemoteKey.DPAD_CENTER -> "Confirm"
        RemoteKey.BACK -> "Return"
        RemoteKey.HOME -> "Home"
        RemoteKey.VOLUME_UP -> "VolumeUp"
        RemoteKey.VOLUME_DOWN -> "VolumeDown"
        RemoteKey.MUTE -> "Mute"
        RemoteKey.POWER -> "Power"
        RemoteKey.CHANNEL_UP -> "ChannelUp"
        RemoteKey.CHANNEL_DOWN -> "ChannelDown"
        RemoteKey.INPUT_SOURCE -> "Input"
        else -> null
    }

    private fun fallbackIrccCodeFor(key: RemoteKey): String? = when (key) {
        RemoteKey.DPAD_UP -> "AAAAAQAAAAEAAAB0Aw=="
        RemoteKey.DPAD_DOWN -> "AAAAAQAAAAEAAAB1Aw=="
        RemoteKey.DPAD_LEFT -> "AAAAAQAAAAEAAAA0Aw=="
        RemoteKey.DPAD_RIGHT -> "AAAAAQAAAAEAAAAzAw=="
        RemoteKey.DPAD_CENTER -> "AAAAAQAAAAEAAABlAw=="
        RemoteKey.BACK -> "AAAAAgAAAJcAAAAjAw=="
        RemoteKey.HOME -> "AAAAAQAAAAEAAAAgAw=="
        RemoteKey.VOLUME_UP -> "AAAAAQAAAAEAAAASAw=="
        RemoteKey.VOLUME_DOWN -> "AAAAAQAAAAEAAAATAw=="
        RemoteKey.MUTE -> "AAAAAQAAAAEAAAAUAw=="
        RemoteKey.POWER -> "AAAAAQAAAAEAAAAVAw=="
        RemoteKey.CHANNEL_UP -> "AAAAAQAAAAEAAAAQAw=="
        RemoteKey.CHANNEL_DOWN -> "AAAAAQAAAAEAAAARAw=="
        RemoteKey.INPUT_SOURCE -> "AAAAAQAAAAEAAAAlAw=="
        else -> null
    }
}
