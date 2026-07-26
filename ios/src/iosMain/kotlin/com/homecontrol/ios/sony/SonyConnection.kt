package com.homecontrol.ios.sony

import com.homecontrol.core.model.RemoteKey
import platform.Foundation.NSUUID

class SonyHandshakeException(message: String) : Exception(message)
class SonyInvalidCodeException(message: String) : Exception(message)
class SonyPairingRequiredException(message: String) : Exception(message)

sealed interface SonyBeginPairingOutcome {
    data object Registered : SonyBeginPairingOutcome
    data object PinRequired : SonyBeginPairingOutcome
}

/**
 * iOS-side wrapper around [SonyApiClient], covering the same ground as the
 * Android plugin's `pair`/`connect`/`sendKey`. Shaped a little differently
 * from [com.homecontrol.ios.samsung.SamsungConnection]/[com.homecontrol.ios.adb.AdbConnection]
 * at the call site because Sony's pairing is genuinely two steps
 * ([beginPairing] then, only if it asks, [completePairing] with the PIN read
 * off the TV screen) rather than one blocking on-screen-approval call --
 * see [com.homecontrol.ios.screens.devices.DevicePairing]'s
 * `PairingUiState.AwaitingCode` for how that surfaces in the UI.
 *
 * PSK-based pairing (a pre-shared key set directly in the TV's IP Control
 * settings, sidestepping `actRegister` trust entirely) isn't wired into the
 * iOS pairing UI in this pass -- the Android app's own doc comment frames it
 * as a fallback for Bravia firmware that won't reliably remember `actRegister`
 * trust, not the primary path; PIN pairing covers the common case matching
 * "Add device" parity with Android for this first iOS pass.
 */
class SonyConnection(private val deviceStore: SonyDeviceStore = SonyDeviceStore()) {

    private var client: SonyApiClient? = null
    private var remoteCodes: Map<String, String> = emptyMap()

    // Held between beginPairing() and completePairing() for the same reason
    // AndroidTvPlugin's pendingRegistrations map exists -- the client and
    // clientid from the first actRegister call have to be reused for the
    // second, not regenerated.
    private var pendingClient: SonyApiClient? = null
    private var pendingClientId: String? = null

    suspend fun beginPairing(ipAddress: String): SonyBeginPairingOutcome {
        val newClient = SonyApiClient(ipAddress)
        val clientId = NSUUID().UUIDString
        return when (val outcome = newClient.beginRegistration(clientId)) {
            RegistrationOutcome.Registered -> {
                onRegistered(ipAddress, clientId, newClient)
                SonyBeginPairingOutcome.Registered
            }
            RegistrationOutcome.PinRequired -> {
                pendingClient = newClient
                pendingClientId = clientId
                SonyBeginPairingOutcome.PinRequired
            }
            is RegistrationOutcome.Failed -> throw SonyHandshakeException(outcome.reason)
        }
    }

    suspend fun completePairing(ipAddress: String, code: String) {
        val pending = pendingClient ?: throw SonyHandshakeException("No pairing in progress")
        val clientId = pendingClientId ?: throw SonyHandshakeException("No pairing in progress")
        pendingClient = null
        pendingClientId = null

        when (val outcome = pending.completeRegistration(clientId, code)) {
            RegistrationOutcome.Registered -> onRegistered(ipAddress, clientId, pending)
            RegistrationOutcome.PinRequired -> throw SonyInvalidCodeException("That code wasn't accepted — check the TV screen and try again")
            is RegistrationOutcome.Failed -> throw SonyHandshakeException(outcome.reason)
        }
    }

    private suspend fun onRegistered(ipAddress: String, clientId: String, apiClient: SonyApiClient) {
        client = apiClient
        deviceStore.save(SonyDeviceStore.Record(ipAddress, clientId = clientId, psk = null))
        remoteCodes = apiClient.getRemoteControllerCodes().orEmpty()
    }

    suspend fun connect(ipAddress: String) {
        if (client != null) return

        val record = deviceStore.find(ipAddress) ?: throw SonyPairingRequiredException("$ipAddress was never paired")

        val psk = record.psk
        if (psk != null) {
            val newClient = SonyApiClient(ipAddress, psk = psk)
            val codes = newClient.getRemoteControllerCodes()
                ?: throw SonyHandshakeException("Couldn't reconnect to $ipAddress")
            client = newClient
            remoteCodes = codes
            return
        }

        val clientId = record.clientId ?: throw SonyPairingRequiredException("$ipAddress was never paired")
        val newClient = SonyApiClient(ipAddress)
        when (val outcome = newClient.beginRegistration(clientId)) {
            RegistrationOutcome.Registered -> onRegistered(ipAddress, clientId, newClient)
            // The TV no longer recognizes this clientid -- a fresh PIN pairing is needed.
            RegistrationOutcome.PinRequired -> throw SonyPairingRequiredException("$ipAddress needs to be re-paired")
            is RegistrationOutcome.Failed -> throw SonyHandshakeException("Couldn't reconnect to $ipAddress")
        }
    }

    fun disconnect() {
        client?.close()
        client = null
        remoteCodes = emptyMap()
    }

    suspend fun sendKey(key: RemoteKey) {
        val activeClient = client ?: throw SonyHandshakeException("Not connected — call connect() first")
        val code = irccCodeFor(key) ?: return
        activeClient.sendIrcc(code)
    }

    suspend fun getSources(): List<Pair<String, String>> {
        val activeClient = client ?: return emptyList()
        return activeClient.getExternalInputs().orEmpty()
    }

    suspend fun selectSource(uri: String) {
        client?.setActiveInput(uri)
    }

    private fun irccCodeFor(key: RemoteKey): String? {
        val sonyName = sonyNameFor(key) ?: return null
        return remoteCodes[sonyName] ?: fallbackIrccCodeFor(key)
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
