package com.homecontrol.core.data

import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.PairingInput
import com.homecontrol.core.model.PairingResult
import kotlinx.coroutines.flow.Flow

/**
 * The repository-layer seam between features and device control: features
 * depend on this interface and on `core:model` only, never on a concrete
 * plugin or on `core:database` directly.
 */
interface PairedDeviceRepository {

    fun observePairedDevices(): Flow<List<PairedDevice>>

    /** Whether any registered plugin claims [discovered] — used to hide network noise (routers, printers, other UPnP chatter) from the device list. */
    fun isPairable(discovered: DiscoveredDevice): Boolean

    /** Finds the plugin that owns [discovered] and asks it to pair, persisting the result on success. */
    suspend fun pair(discovered: DiscoveredDevice, input: PairingInput): PairingResult

    suspend fun removeDevice(device: PairedDevice)

    /** User-chosen display name (e.g. "Bedroom TV") — purely local labeling, never sent to the device itself. */
    suspend fun renameDevice(device: PairedDevice, newName: String)
}
