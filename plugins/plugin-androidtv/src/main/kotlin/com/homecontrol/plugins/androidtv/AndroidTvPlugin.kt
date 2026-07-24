package com.homecontrol.plugins.androidtv

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
import com.homecontrol.core.model.unreachablePortMessage
import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.plugins.androidtv.adb.ADB_PORT
import com.homecontrol.plugins.androidtv.adb.AdbApprovalTimeoutException
import com.homecontrol.plugins.androidtv.adb.AdbConnection
import com.homecontrol.plugins.androidtv.adb.AdbKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

private const val PLUGIN_ID = "androidtv"
private val SONY_MODEL_PREFIXES = listOf("kd-", "kdl-", "xr-", "xbr-")

/**
 * Android TV / Google TV, controlled over classic ADB-over-TCP (port 5555).
 * Pairing is [PairingStrategy.ON_SCREEN_APPROVAL] — the device shows its own
 * "Allow debugging?" prompt, there's no code for the user to type. See
 * `plugins/plugin-androidtv/src/main/kotlin/.../adb/AdbConnection.kt` for
 * the wire protocol itself.
 */
class AndroidTvPlugin(
    private val keyStore: AdbKeyStore,
) : IDevicePlugin {

    override val pluginId: String = PLUGIN_ID
    override val displayName: String = "Android TV"
    override val supportedDeviceTypes: Set<DeviceType> =
        setOf(DeviceType.ANDROID_TV, DeviceType.GOOGLE_TV, DeviceType.FIRE_TV)
    override val pairingStrategy: PairingStrategy = PairingStrategy.ON_SCREEN_APPROVAL

    private val connections = ConcurrentHashMap<String, AdbConnection>()

    override fun canHandle(discovered: DiscoveredDevice): Boolean {
        val serviceType = discovered.serviceRecords["serviceType"].orEmpty()
        val nameAndModel = "${discovered.name} ${discovered.model.orEmpty()}".lowercase()

        // Sony Android TVs do run real ADB once the network actually allows
        // it (a router firewall, not a firmware limitation, was the earlier
        // blocker) — but on real hardware, the on-screen approval dialog
        // this protocol depends on has proven unreliable to get on fresh
        // reconnects (sometimes just never appears, leaving the connection
        // hanging for the full approval timeout). plugin-sonytv's own PIN-based
        // pairing doesn't share that failure mode, so Sony-branded devices
        // are deferred to it instead — see its canHandle().
        val looksLikeSony = nameAndModel.contains("sony") ||
            nameAndModel.contains("bravia") ||
            SONY_MODEL_PREFIXES.any { nameAndModel.contains(it) }
        if (looksLikeSony) return false

        return discovered.deviceTypeHint == DeviceType.ANDROID_TV ||
            discovered.deviceTypeHint == DeviceType.GOOGLE_TV ||
            discovered.deviceTypeHint == DeviceType.FIRE_TV ||
            // NOTE: Sky Stream was tried and ruled out here — confirmed to run
            // Comcast's RDK ("Entertainment OS"), a closed platform with no
            // ADB/developer-options surface at all, not an Android TV/Google TV
            // license like Fire TV. Don't re-add Sky matching to this plugin
            // without a genuinely different protocol to speak to it.
            // The Android TV Remote Service v2 mDNS advertisement is the
            // strongest signal — genuinely OS-level, not vendor-specific.
            serviceType.contains("androidtvremote") ||
            nameAndModel.contains("android tv") ||
            nameAndModel.contains("google tv") ||
            nameAndModel.contains("chromecast") ||
            // Fire OS exposes the same ADB-over-TCP surface this plugin
            // already speaks, but Fire TV has no discovery broadcast of its
            // own — this only ever matches a manually-added device (see
            // feature-devices' "Add by IP" flow), never real network traffic.
            nameAndModel.contains("fire tv") ||
            nameAndModel.contains("firetv") ||
            nameAndModel.contains("amazon fire")
    }

    override suspend fun pair(discovered: DiscoveredDevice, input: PairingInput): PairingResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = AdbConnection.open(discovered.ipAddress, keyStore.getOrCreateKeyPair())
                connections[discovered.ipAddress] = connection

                val deviceType = discovered.deviceTypeHint.takeIf { it in supportedDeviceTypes } ?: DeviceType.ANDROID_TV
                PairingResult.Success(
                    PairedDevice(
                        id = "$PLUGIN_ID:${discovered.ipAddress}",
                        name = discovered.name,
                        manufacturer = discovered.manufacturer ?: defaultManufacturer(deviceType),
                        model = discovered.model ?: defaultModel(deviceType),
                        ipAddress = discovered.ipAddress,
                        macAddress = discovered.macAddress,
                        deviceType = deviceType,
                        firmwareVersion = discovered.firmwareVersion,
                        capabilities = capabilities(),
                        isOnline = true,
                        pluginId = PLUGIN_ID,
                    ),
                )
            } catch (approvalTimeout: AdbApprovalTimeoutException) {
                PairingResult.Failed(
                    PairingFailureReason.TIMEOUT,
                    "The TV showed an \"Allow debugging?\" prompt, but it wasn't approved in time. Check the TV screen and tap Allow within 2 minutes, then try again.",
                )
            } catch (timeout: SocketTimeoutException) {
                PairingResult.Failed(PairingFailureReason.TIMEOUT, unreachablePortMessage(discovered.ipAddress, ADB_PORT))
            } catch (error: Exception) {
                PairingResult.Failed(PairingFailureReason.NETWORK_ERROR, error.message)
            }
        }

    override suspend fun connect(device: PairedDevice): ConnectionResult = withContext(Dispatchers.IO) {
        android.util.Log.d("AndroidTvPlugin", "connect: starting for ${device.ipAddress}")
        // Pairing (or a previous connect()) may have left a perfectly good,
        // already-authenticated connection open for this device — reuse it
        // rather than tearing it down and immediately reopening a new one.
        // On at least one real TV, that immediate close-then-reopen churn is
        // itself what triggered a fast, unexplained connection failure.
        if (connections[device.ipAddress] != null) {
            android.util.Log.d("AndroidTvPlugin", "connect: reusing existing connection")
            return@withContext ConnectionResult.Connected
        }

        try {
            android.util.Log.d("AndroidTvPlugin", "connect: fetching keypair")
            val keyPair = keyStore.getOrCreateKeyPair()
            android.util.Log.d("AndroidTvPlugin", "connect: got keypair, opening AdbConnection")
            connections[device.ipAddress] = AdbConnection.open(device.ipAddress, keyPair)
            android.util.Log.d("AndroidTvPlugin", "connect: success")
            ConnectionResult.Connected
        } catch (approvalTimeout: AdbApprovalTimeoutException) {
            ConnectionResult.Failed("The TV showed an \"Allow debugging?\" prompt, but it wasn't approved in time. Check the TV screen and tap Allow within 2 minutes, then try again.")
        } catch (timeout: SocketTimeoutException) {
            ConnectionResult.Failed(unreachablePortMessage(device.ipAddress, ADB_PORT))
        } catch (error: Exception) {
            android.util.Log.d("AndroidTvPlugin", "connect: failed with ${error::class.simpleName}: ${error.message}")
            ConnectionResult.Failed(error.message ?: "Unable to connect (${error::class.simpleName})")
        }
    }

    override suspend fun disconnect(device: PairedDevice) {
        connections.remove(device.ipAddress)?.close()
    }

    override suspend fun getCapabilities(device: PairedDevice): DeviceCapabilities = capabilities()

    override suspend fun sendKey(device: PairedDevice, key: RemoteKey) {
        shell(device, "input keyevent ${keyCodeFor(key)}")
    }

    override suspend fun powerOn(device: PairedDevice) = sendKey(device, RemoteKey.POWER)

    override suspend fun powerOff(device: PairedDevice) = sendKey(device, RemoteKey.POWER)

    override suspend fun volumeUp(device: PairedDevice) {
        shell(device, "input keyevent KEYCODE_VOLUME_UP")
    }

    override suspend fun volumeDown(device: PairedDevice) {
        shell(device, "input keyevent KEYCODE_VOLUME_DOWN")
    }

    override suspend fun mute(device: PairedDevice) {
        shell(device, "input keyevent KEYCODE_VOLUME_MUTE")
    }

    override suspend fun sendText(device: PairedDevice, text: String) {
        shell(device, "input text ${text.replace(" ", "%s")}")
    }

    override suspend fun launchApp(device: PairedDevice, appId: String) {
        shell(device, "monkey -p $appId -c android.intent.category.LAUNCHER 1")
    }

    override suspend fun getInstalledApps(device: PairedDevice): List<AppInfo> {
        val output = shell(device, "pm list packages -3")
        return output.lineSequence()
            .mapNotNull { line -> line.removePrefix("package:").trim().takeIf { it.isNotEmpty() } }
            .map { packageId -> AppInfo(appId = packageId, displayName = packageId) }
            .toList()
    }

    private fun defaultManufacturer(deviceType: DeviceType): String = when (deviceType) {
        DeviceType.FIRE_TV -> "Amazon"
        else -> "Unknown"
    }

    private fun defaultModel(deviceType: DeviceType): String = when (deviceType) {
        DeviceType.FIRE_TV -> "Fire TV"
        else -> "Android TV"
    }

    private fun capabilities() = DeviceCapabilities(
        supportsPower = true,
        supportsVolume = true,
        supportsKeyboard = true,
        supportsApps = true,
        supportsMediaTransport = true,
        supportsVoiceAssist = true,
    )

    private suspend fun shell(device: PairedDevice, command: String): String = withContext(Dispatchers.IO) {
        val connection = connections[device.ipAddress]
            ?: error("Not connected to ${device.name} — call connect() first")
        connection.shell(command)
    }

    private fun keyCodeFor(key: RemoteKey): String = when (key) {
        RemoteKey.DPAD_UP -> "KEYCODE_DPAD_UP"
        RemoteKey.DPAD_DOWN -> "KEYCODE_DPAD_DOWN"
        RemoteKey.DPAD_LEFT -> "KEYCODE_DPAD_LEFT"
        RemoteKey.DPAD_RIGHT -> "KEYCODE_DPAD_RIGHT"
        RemoteKey.DPAD_CENTER -> "KEYCODE_DPAD_CENTER"
        RemoteKey.BACK -> "KEYCODE_BACK"
        RemoteKey.HOME -> "KEYCODE_HOME"
        RemoteKey.MENU -> "KEYCODE_MENU"
        RemoteKey.PLAY -> "KEYCODE_MEDIA_PLAY"
        RemoteKey.PAUSE -> "KEYCODE_MEDIA_PAUSE"
        RemoteKey.PLAY_PAUSE -> "KEYCODE_MEDIA_PLAY_PAUSE"
        RemoteKey.STOP -> "KEYCODE_MEDIA_STOP"
        RemoteKey.FAST_FORWARD -> "KEYCODE_MEDIA_FAST_FORWARD"
        RemoteKey.REWIND -> "KEYCODE_MEDIA_REWIND"
        RemoteKey.VOLUME_UP -> "KEYCODE_VOLUME_UP"
        RemoteKey.VOLUME_DOWN -> "KEYCODE_VOLUME_DOWN"
        RemoteKey.MUTE -> "KEYCODE_VOLUME_MUTE"
        RemoteKey.POWER -> "KEYCODE_POWER"
        RemoteKey.BACKSPACE -> "KEYCODE_DEL"
        RemoteKey.CHANNEL_UP -> "KEYCODE_CHANNEL_UP"
        RemoteKey.CHANNEL_DOWN -> "KEYCODE_CHANNEL_DOWN"
        RemoteKey.INPUT_SOURCE -> "KEYCODE_TV_INPUT"
        RemoteKey.SMART_HUB -> "KEYCODE_TV_CONTENTS_MENU"
        RemoteKey.VOICE_ASSIST -> "KEYCODE_ASSIST"
    }
}
