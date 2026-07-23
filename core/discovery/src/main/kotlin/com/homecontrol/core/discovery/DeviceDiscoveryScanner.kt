package com.homecontrol.core.discovery

import com.homecontrol.core.model.DiscoveredDevice
import kotlinx.coroutines.flow.Flow

/**
 * Scans the local network via mDNS, SSDP, and UDP broadcast, merged into a
 * single brand-agnostic stream. Matching a [DiscoveredDevice] to the plugin
 * that owns it happens later, in `core:data` — this module only reports what
 * the network said.
 */
interface DeviceDiscoveryScanner {

    /** Runs a single bounded scan and completes — collect fresh per scan attempt. */
    fun scan(): Flow<DiscoveredDevice>
}
