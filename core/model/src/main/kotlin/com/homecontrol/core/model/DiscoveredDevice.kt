package com.homecontrol.core.model

/**
 * A raw, not-yet-paired record produced by `core:discovery`. Brand-agnostic
 * by design: discovery only reports what the network told it, it never
 * decides which plugin owns the device — see [DeviceType] hint vs. each
 * plugin's own `canHandle` check for that.
 */
data class DiscoveredDevice(
    val discoveryId: String,
    val name: String,
    val ipAddress: String,
    val port: Int? = null,
    val macAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmwareVersion: String? = null,
    val deviceTypeHint: DeviceType = DeviceType.UNKNOWN,
    val discoveryProtocol: DiscoveryProtocol,
    val serviceRecords: Map<String, String> = emptyMap(),
)

/** Which discovery mechanism found the device — a device may be seen by more than one. */
enum class DiscoveryProtocol {
    MDNS,
    SSDP,
    UDP_BROADCAST,
}
