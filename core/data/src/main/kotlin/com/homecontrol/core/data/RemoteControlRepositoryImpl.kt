package com.homecontrol.core.data

import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.ConnectionResult
import com.homecontrol.core.model.MouseButton
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.RemoteKey
import com.homecontrol.core.pluginapi.PluginRegistry

class RemoteControlRepositoryImpl(
    private val pluginRegistry: PluginRegistry,
) : RemoteControlRepository {

    override suspend fun connect(device: PairedDevice): ConnectionResult {
        val plugin = pluginRegistry.findPluginById(device.pluginId)
            ?: return ConnectionResult.Failed("No plugin registered for ${device.pluginId}")
        return plugin.connect(device)
    }

    override suspend fun sendKey(device: PairedDevice, key: RemoteKey) {
        pluginRegistry.findPluginById(device.pluginId)?.sendKey(device, key)
    }

    override suspend fun powerOn(device: PairedDevice) {
        pluginRegistry.findPluginById(device.pluginId)?.powerOn(device)
    }

    override suspend fun powerOff(device: PairedDevice) {
        pluginRegistry.findPluginById(device.pluginId)?.powerOff(device)
    }

    override suspend fun volumeUp(device: PairedDevice) {
        pluginRegistry.findPluginById(device.pluginId)?.volumeUp(device)
    }

    override suspend fun volumeDown(device: PairedDevice) {
        pluginRegistry.findPluginById(device.pluginId)?.volumeDown(device)
    }

    override suspend fun mute(device: PairedDevice) {
        pluginRegistry.findPluginById(device.pluginId)?.mute(device)
    }

    override suspend fun sendText(device: PairedDevice, text: String) {
        pluginRegistry.findPluginById(device.pluginId)?.sendText(device, text)
    }

    override suspend fun moveMouse(device: PairedDevice, deltaX: Int, deltaY: Int) {
        pluginRegistry.findPluginById(device.pluginId)?.moveMouse(device, deltaX, deltaY)
    }

    override suspend fun clickMouse(device: PairedDevice, button: MouseButton) {
        pluginRegistry.findPluginById(device.pluginId)?.clickMouse(device, button)
    }

    override suspend fun launchApp(device: PairedDevice, target: String) {
        pluginRegistry.findPluginById(device.pluginId)?.launchApp(device, target)
    }

    override suspend fun getInstalledApps(device: PairedDevice): List<AppInfo> =
        pluginRegistry.findPluginById(device.pluginId)?.getInstalledApps(device) ?: emptyList()

    override suspend fun addAppButton(device: PairedDevice, label: String, target: String): Boolean =
        pluginRegistry.findPluginById(device.pluginId)?.addAppButton(device, label, target) ?: false

    override suspend fun getQuickActions(device: PairedDevice): List<AppInfo> =
        pluginRegistry.findPluginById(device.pluginId)?.getQuickActions(device) ?: emptyList()

    override suspend fun triggerQuickAction(device: PairedDevice, actionId: String) {
        pluginRegistry.findPluginById(device.pluginId)?.triggerQuickAction(device, actionId)
    }

    override suspend fun getSources(device: PairedDevice): List<AppInfo> =
        pluginRegistry.findPluginById(device.pluginId)?.getSources(device) ?: emptyList()

    override suspend fun selectSource(device: PairedDevice, sourceId: String) {
        pluginRegistry.findPluginById(device.pluginId)?.selectSource(device, sourceId)
    }
}
