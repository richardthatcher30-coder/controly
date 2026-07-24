package com.homecontrol.core.model

/** Outcome of [com.homecontrol.core.pluginapi.IDevicePlugin.connect]. */
sealed interface ConnectionResult {
    data object Connected : ConnectionResult

    /** The stored token was rejected — the device was reset, revoked, or re-paired elsewhere. */
    data object PairingRequired : ConnectionResult

    data class Failed(val reason: String) : ConnectionResult
}
