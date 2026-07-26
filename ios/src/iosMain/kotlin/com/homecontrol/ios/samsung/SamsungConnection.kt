package com.homecontrol.ios.samsung

import com.homecontrol.core.model.RemoteKey

class SamsungPairingRejectedException(message: String) : Exception(message)
class SamsungPairingTimeoutException(message: String) : Exception(message)
class SamsungHandshakeException(message: String) : Exception(message)

/**
 * iOS-side wrapper around [SamsungLegacyClient], shaped like
 * [com.homecontrol.ios.adb.AdbConnection] (`pair`/`connect`/`disconnect`
 * blocking calls, one instance per screen) so [RemoteScreen] and
 * [com.homecontrol.ios.screens.devices.DevicePairing] can treat it uniformly
 * alongside ADB and Companion-protocol devices.
 *
 * Wake-on-LAN power-on isn't implemented here (unlike the Android plugin):
 * that relies on resolving the TV's MAC address from the OS ARP cache after
 * a real TCP connection populates it (`/proc/net/arp`), which has no iOS
 * equivalent -- a sandboxed app can't read the system ARP table at all.
 * `capabilities()`-equivalent gating for this iOS build should reflect that
 * `supportsWakeOnLan = false`, rather than showing a Power-on control that
 * silently does nothing.
 */
class SamsungConnection {

    private var client: SamsungLegacyClient? = null

    fun pair(ipAddress: String) {
        val client = SamsungLegacyClient(ipAddress, SamsungClientIdentity().getOrCreate())
        when (val outcome = client.connect()) {
            SamsungLegacyOutcome.Connected -> this.client = client
            SamsungLegacyOutcome.Denied -> {
                client.close()
                throw SamsungPairingRejectedException("Pairing was declined on the TV")
            }
            SamsungLegacyOutcome.TimedOut -> {
                client.close()
                throw SamsungPairingTimeoutException("The TV showed an Allow/Deny prompt, but it wasn't answered in time")
            }
            is SamsungLegacyOutcome.Failed -> {
                client.close()
                throw SamsungHandshakeException(outcome.reason)
            }
        }
    }

    /**
     * Always rebuilds rather than checking for a live client — this legacy
     * protocol's handshake is cheap and safe to repeat (the TV either
     * re-grants silently or re-prompts; see [SamsungLegacyClient]'s doc
     * comment), unlike ADB's connection where unconditional rebuilding was a
     * real regression on real hardware.
     */
    fun connect(ipAddress: String) {
        client?.close()
        val newClient = SamsungLegacyClient(ipAddress, SamsungClientIdentity().getOrCreate())
        when (val outcome = newClient.connect()) {
            SamsungLegacyOutcome.Connected -> client = newClient
            SamsungLegacyOutcome.Denied -> {
                newClient.close()
                throw SamsungPairingRejectedException("The TV no longer recognizes this app — re-pair from the Devices screen")
            }
            SamsungLegacyOutcome.TimedOut -> {
                newClient.close()
                throw SamsungPairingTimeoutException("Timed out connecting to $ipAddress")
            }
            is SamsungLegacyOutcome.Failed -> {
                newClient.close()
                throw SamsungHandshakeException(outcome.reason)
            }
        }
    }

    fun disconnect() {
        client?.close()
        client = null
    }

    fun sendKey(key: RemoteKey) {
        val code = keyCodeFor(key) ?: return
        val activeClient = client ?: throw SamsungHandshakeException("Not connected — call connect() first")
        try {
            activeClient.sendKey(code)
        } catch (error: Exception) {
            client = null
            activeClient.close()
            throw error
        }
    }

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
