package com.homecontrol.core.data

import com.homecontrol.core.database.PairedDeviceDao
import com.homecontrol.core.database.PairedDeviceEntity
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.PairingFailureReason
import com.homecontrol.core.model.PairingInput
import com.homecontrol.core.model.PairingResult
import com.homecontrol.core.pluginapi.PluginRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PairedDeviceRepositoryImpl(
    private val dao: PairedDeviceDao,
    private val pluginRegistry: PluginRegistry,
) : PairedDeviceRepository {

    override fun observePairedDevices(): Flow<List<PairedDevice>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun isPairable(discovered: DiscoveredDevice): Boolean =
        pluginRegistry.findPluginFor(discovered) != null

    override suspend fun pair(discovered: DiscoveredDevice, input: PairingInput): PairingResult {
        val plugin = pluginRegistry.findPluginFor(discovered)
            ?: return PairingResult.Failed(PairingFailureReason.NETWORK_ERROR)

        val result = plugin.pair(discovered, input)
        if (result is PairingResult.Success) {
            dao.upsert(result.pairedDevice.toEntity())
        }
        return result
    }

    override suspend fun removeDevice(device: PairedDevice) {
        dao.delete(device.toEntity())
    }

    override suspend fun renameDevice(device: PairedDevice, newName: String) {
        dao.upsert(device.copy(name = newName).toEntity())
    }
}

private fun PairedDeviceEntity.toDomain() = PairedDevice(
    id = id,
    name = name,
    manufacturer = manufacturer,
    model = model,
    ipAddress = ipAddress,
    macAddress = macAddress,
    deviceType = DeviceType.valueOf(deviceType),
    firmwareVersion = firmwareVersion,
    capabilities = DeviceCapabilities(
        supportsPower = supportsPower,
        supportsVolume = supportsVolume,
        supportsKeyboard = supportsKeyboard,
        supportsApps = supportsApps,
        supportsTouchpad = supportsTouchpad,
        supportsMouse = supportsMouse,
        supportsPTZ = supportsPTZ,
        supportsWakeOnLan = supportsWakeOnLan,
        supportsScreenMirror = supportsScreenMirror,
        supportsChannels = supportsChannels,
        supportsInputSource = supportsInputSource,
        supportsQuickActions = supportsQuickActions,
        supportsSourceCycle = supportsSourceCycle,
        supportsSmartHub = supportsSmartHub,
        supportsMediaTransport = supportsMediaTransport,
        supportsVoiceAssist = supportsVoiceAssist,
    ),
    isOnline = true,
    pluginId = pluginId,
)

private fun PairedDevice.toEntity() = PairedDeviceEntity(
    id = id,
    name = name,
    manufacturer = manufacturer,
    model = model,
    ipAddress = ipAddress,
    macAddress = macAddress,
    deviceType = deviceType.name,
    firmwareVersion = firmwareVersion,
    pluginId = pluginId,
    supportsPower = capabilities.supportsPower,
    supportsVolume = capabilities.supportsVolume,
    supportsKeyboard = capabilities.supportsKeyboard,
    supportsApps = capabilities.supportsApps,
    supportsTouchpad = capabilities.supportsTouchpad,
    supportsMouse = capabilities.supportsMouse,
    supportsPTZ = capabilities.supportsPTZ,
    supportsWakeOnLan = capabilities.supportsWakeOnLan,
    supportsScreenMirror = capabilities.supportsScreenMirror,
    supportsChannels = capabilities.supportsChannels,
    supportsInputSource = capabilities.supportsInputSource,
    supportsQuickActions = capabilities.supportsQuickActions,
    supportsSourceCycle = capabilities.supportsSourceCycle,
    supportsSmartHub = capabilities.supportsSmartHub,
    supportsMediaTransport = capabilities.supportsMediaTransport,
    supportsVoiceAssist = capabilities.supportsVoiceAssist,
)
