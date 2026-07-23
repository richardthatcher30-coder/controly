package com.homecontrol.plugins.windows

import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.ConnectionResult
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.DiscoveryProtocol
import com.homecontrol.core.model.MouseButton
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.PairingFailureReason
import com.homecontrol.core.model.PairingInput
import com.homecontrol.core.model.PairingResult
import com.homecontrol.core.model.PairingStrategy
import com.homecontrol.core.model.RemoteKey
import com.homecontrol.core.model.unreachablePortMessage
import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.plugins.windows.crypto.CompanionKeyStore
import com.homecontrol.plugins.windows.crypto.WindowsDeviceRecord
import com.homecontrol.plugins.windows.crypto.WindowsDeviceStore
import com.homecontrol.plugins.windows.networking.COMPANION_PORT
import com.homecontrol.plugins.windows.networking.CompanionSession
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

private const val PLUGIN_ID = "windows"
private const val WAKE_ON_LAN_PORT = 9

/**
 * Windows PC, controlled via our own protocol (`plugin-windows` ↔
 * `HomeControl.Companion`, see windows-companion/README-equivalent doc
 * comments on the C# side) — ECDH pairing verified by on-screen approval,
 * commands over the same authenticated WebSocket connection.
 *
 * Not yet exposed through [IDevicePlugin]: restart/sleep specifically —
 * only power-off (mapped to shutdown) and Wake-on-LAN map onto the current
 * interface's power methods, even though the companion already implements
 * restart/sleep too.
 */
@Singleton
class WindowsPlugin @Inject constructor(
    private val keyStore: CompanionKeyStore,
    private val deviceStore: WindowsDeviceStore,
) : IDevicePlugin {

    override val pluginId: String = PLUGIN_ID
    override val displayName: String = "Windows PC"
    override val supportedDeviceTypes: Set<DeviceType> = setOf(DeviceType.WINDOWS_PC)
    override val pairingStrategy: PairingStrategy = PairingStrategy.ON_SCREEN_APPROVAL

    private val sessions = ConcurrentHashMap<String, CompanionSession>()

    override fun canHandle(discovered: DiscoveredDevice): Boolean =
        discovered.discoveryProtocol == DiscoveryProtocol.UDP_BROADCAST

    override suspend fun pair(discovered: DiscoveredDevice, input: PairingInput): PairingResult =
        withContext(Dispatchers.IO) {
            try {
                // No pinned fingerprint here, even if this IP was paired
                // before: pairing is the user explicitly (re-)establishing
                // trust, so it must always succeed against whatever
                // certificate the PC presents right now — e.g. after the
                // companion was reinstalled or its cert regenerated, leaving
                // a stale fingerprint pinned from an earlier pairing at the
                // same address. Strict pinning is only for connect()/reconnect,
                // where a mismatch really would mean impersonation.
                val session = CompanionSession.connect(
                    ipAddress = discovered.ipAddress,
                    keyPair = keyStore.getOrCreateKeyPair(),
                    pinnedFingerprint = null,
                )

                val approved = session.pair(deviceName = "HomeControl")
                if (!approved) {
                    session.close()
                    return@withContext PairingResult.Failed(PairingFailureReason.REJECTED_BY_DEVICE)
                }

                val serverPublicKey = session.serverPublicKeyBase64
                if (serverPublicKey == null) {
                    session.close()
                    return@withContext PairingResult.Failed(PairingFailureReason.NETWORK_ERROR)
                }

                deviceStore.save(
                    WindowsDeviceRecord(
                        ipAddress = discovered.ipAddress,
                        serverPublicKeyBase64 = serverPublicKey,
                        certificateFingerprint = session.certificateFingerprint,
                    ),
                )
                sessions[discovered.ipAddress] = session

                PairingResult.Success(
                    PairedDevice(
                        id = "$PLUGIN_ID:${discovered.ipAddress}",
                        name = discovered.name,
                        manufacturer = discovered.manufacturer ?: "PC",
                        model = discovered.model ?: "Windows",
                        ipAddress = discovered.ipAddress,
                        macAddress = discovered.macAddress,
                        deviceType = DeviceType.WINDOWS_PC,
                        firmwareVersion = discovered.firmwareVersion,
                        capabilities = capabilities(),
                        isOnline = true,
                        pluginId = PLUGIN_ID,
                    ),
                )
            } catch (timeout: TimeoutCancellationException) {
                PairingResult.Failed(PairingFailureReason.TIMEOUT)
            } catch (unreachable: ConnectException) {
                PairingResult.Failed(PairingFailureReason.NETWORK_ERROR, unreachablePortMessage(discovered.ipAddress, COMPANION_PORT))
            } catch (unreachable: SocketTimeoutException) {
                PairingResult.Failed(PairingFailureReason.NETWORK_ERROR, unreachablePortMessage(discovered.ipAddress, COMPANION_PORT))
            } catch (error: Exception) {
                PairingResult.Failed(PairingFailureReason.NETWORK_ERROR, error.message)
            }
        }

    override suspend fun connect(device: PairedDevice): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            sessions.remove(device.ipAddress)?.close()

            val record = deviceStore.find(device.ipAddress) ?: return@withContext ConnectionResult.PairingRequired
            val session = CompanionSession.connect(device.ipAddress, keyStore.getOrCreateKeyPair(), record.certificateFingerprint)
            session.serverPublicKeyBase64 = record.serverPublicKeyBase64

            if (!session.authenticate()) {
                session.close()
                return@withContext ConnectionResult.PairingRequired
            }

            sessions[device.ipAddress] = session
            ConnectionResult.Connected
        } catch (timeout: TimeoutCancellationException) {
            ConnectionResult.Failed("Timed out connecting to ${device.name}")
        } catch (unreachable: ConnectException) {
            ConnectionResult.Failed(unreachablePortMessage(device.ipAddress, COMPANION_PORT))
        } catch (unreachable: SocketTimeoutException) {
            ConnectionResult.Failed(unreachablePortMessage(device.ipAddress, COMPANION_PORT))
        } catch (error: Exception) {
            ConnectionResult.Failed(error.message ?: "Unable to connect")
        }
    }

    override suspend fun disconnect(device: PairedDevice) {
        sessions.remove(device.ipAddress)?.close()
    }

    override suspend fun getCapabilities(device: PairedDevice): DeviceCapabilities = capabilities()

    override suspend fun sendKey(device: PairedDevice, key: RemoteKey) {
        sendCommand(device, "key_event", buildJsonObject { put("key", JsonPrimitive(key.name)) })
    }

    /** The PC has to already be running the companion to have paired at all, so "power on" means Wake-on-LAN from sleep/off. */
    override suspend fun powerOn(device: PairedDevice) {
        val mac = device.macAddress ?: return
        withContext(Dispatchers.IO) { sendWakeOnLanPacket(mac) }
    }

    override suspend fun powerOff(device: PairedDevice) {
        sendCommand(device, "shutdown")
    }

    override suspend fun volumeUp(device: PairedDevice) = sendKey(device, RemoteKey.VOLUME_UP)

    override suspend fun volumeDown(device: PairedDevice) = sendKey(device, RemoteKey.VOLUME_DOWN)

    override suspend fun mute(device: PairedDevice) = sendKey(device, RemoteKey.MUTE)

    override suspend fun sendText(device: PairedDevice, text: String) {
        sendCommand(device, "type_text", buildJsonObject { put("text", JsonPrimitive(text)) })
    }

    override suspend fun moveMouse(device: PairedDevice, deltaX: Int, deltaY: Int) {
        sendCommand(
            device,
            "mouse_move",
            buildJsonObject {
                put("dx", JsonPrimitive(deltaX))
                put("dy", JsonPrimitive(deltaY))
            },
        )
    }

    override suspend fun clickMouse(device: PairedDevice, button: MouseButton) {
        sendCommand(
            device,
            "mouse_click",
            buildJsonObject { put("button", JsonPrimitive(if (button == MouseButton.RIGHT) "right" else "left")) },
        )
    }

    /**
     * [appId] here is whatever the user's custom button was configured
     * with — an app name Windows' "App Paths" registry knows (e.g.
     * "chrome"), a full executable path, or a URL. The companion resolves
     * it the same way Win+R would.
     */
    override suspend fun launchApp(device: PairedDevice, appId: String) {
        sendCommand(device, "launch_app", buildJsonObject { put("target", JsonPrimitive(appId)) })
    }

    /** Real Start Menu shortcuts from the companion (see `InstalledAppsProvider` on the C# side), not a hardcoded list. */
    override suspend fun getInstalledApps(device: PairedDevice): List<AppInfo> {
        val apps = sendCommandForResult(device, "list_apps") as? JsonArray ?: return emptyList()
        return apps.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val appId = obj["appId"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
            val displayName = obj["displayName"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
            val iconBase64 = obj["iconBase64"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            AppInfo(
                appId = appId,
                displayName = displayName,
                iconUri = iconBase64?.let { "data:image/png;base64,$it" },
            )
        }
    }

    /** The phone can only add a URL here — there's no way for it to browse the PC's filesystem for an .exe, that's what the companion's own "Manage app buttons" window is for. */
    override suspend fun addAppButton(device: PairedDevice, label: String, target: String): Boolean {
        sendCommand(
            device,
            "add_url_button",
            buildJsonObject {
                put("label", JsonPrimitive(label))
                put("url", JsonPrimitive(target))
            },
        )
        return true
    }

    override suspend fun getQuickActions(device: PairedDevice): List<AppInfo> {
        val actions = sendCommandForResult(device, "list_quick_actions") as? JsonArray ?: return emptyList()
        return actions.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val appId = obj["appId"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
            val displayName = obj["displayName"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
            AppInfo(appId = appId, displayName = displayName)
        }
    }

    override suspend fun triggerQuickAction(device: PairedDevice, actionId: String) {
        sendCommand(device, "trigger_quick_action", buildJsonObject { put("actionId", JsonPrimitive(actionId)) })
    }

    private fun capabilities() = DeviceCapabilities(
        // Power on/off deliberately hidden for now — not wanted in the UI yet.
        supportsPower = false,
        supportsWakeOnLan = false,
        supportsVolume = true,
        supportsKeyboard = true,
        supportsTouchpad = true,
        supportsMouse = true,
        supportsApps = true,
        supportsQuickActions = true,
    )

    private suspend fun sendCommand(device: PairedDevice, action: String, params: JsonElement? = null) {
        sendCommandForResult(device, action, params)
    }

    private suspend fun sendCommandForResult(device: PairedDevice, action: String, params: JsonElement? = null): JsonElement? =
        withContext(Dispatchers.IO) {
            val session = sessions[device.ipAddress] ?: error("Not connected to ${device.name} — call connect() first")
            session.sendCommand(action, params)
        }

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
}
