package com.homecontrol.core.data

import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.ConnectionResult
import com.homecontrol.core.model.MouseButton
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.RemoteKey

/**
 * The seam between `feature-remote` and the actual device plugin — looks up
 * the right [com.homecontrol.core.pluginapi.IDevicePlugin] by
 * [PairedDevice.pluginId] and delegates. Features never touch
 * [com.homecontrol.core.pluginapi.PluginRegistry] directly.
 */
interface RemoteControlRepository {

    suspend fun connect(device: PairedDevice): ConnectionResult

    suspend fun sendKey(device: PairedDevice, key: RemoteKey)

    suspend fun powerOn(device: PairedDevice)

    suspend fun powerOff(device: PairedDevice)

    suspend fun volumeUp(device: PairedDevice)

    suspend fun volumeDown(device: PairedDevice)

    suspend fun mute(device: PairedDevice)

    suspend fun sendText(device: PairedDevice, text: String)

    suspend fun moveMouse(device: PairedDevice, deltaX: Int, deltaY: Int)

    suspend fun clickMouse(device: PairedDevice, button: MouseButton)

    suspend fun launchApp(device: PairedDevice, target: String)

    suspend fun getInstalledApps(device: PairedDevice): List<AppInfo>

    suspend fun addAppButton(device: PairedDevice, label: String, target: String): Boolean

    suspend fun getQuickActions(device: PairedDevice): List<AppInfo>

    suspend fun triggerQuickAction(device: PairedDevice, actionId: String)

    suspend fun getSources(device: PairedDevice): List<AppInfo>

    suspend fun selectSource(device: PairedDevice, sourceId: String)
}
