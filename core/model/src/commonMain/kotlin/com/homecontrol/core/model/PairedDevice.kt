package com.homecontrol.core.model

/**
 * A device that has completed pairing and is persisted in `core:database`.
 * This is what `feature-devices` and `feature-remote` operate on — never
 * [DiscoveredDevice], which is ephemeral scan output.
 */
data class PairedDevice(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val ipAddress: String,
    val macAddress: String?,
    val deviceType: DeviceType,
    val firmwareVersion: String?,
    val capabilities: DeviceCapabilities,
    val isOnline: Boolean,
    val pluginId: String,
)
