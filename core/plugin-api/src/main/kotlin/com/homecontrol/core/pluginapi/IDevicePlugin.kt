package com.homecontrol.core.pluginapi

import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.ConnectionResult
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.MouseButton
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.PairingInput
import com.homecontrol.core.model.PairingResult
import com.homecontrol.core.model.PairingStrategy
import com.homecontrol.core.model.RemoteKey

/**
 * The contract every device integration implements — Android TV, Fire TV,
 * Samsung TV, Windows Companion, and any future community-contributed
 * plugin. Nothing outside `plugins/` implements this interface, and nothing
 * outside `core:data` + `plugins:plugin-registry` holds a concrete reference
 * to an implementation: features only ever see this interface plus
 * [DeviceCapabilities].
 *
 * Control functions are only called for capabilities the plugin itself
 * reported as supported via [getCapabilities] — a plugin backing a device
 * without, say, app launching is never asked to launch an app, so
 * implementations do not need to defensively guard against unsupported
 * calls.
 */
interface IDevicePlugin {

    /** Stable identifier, e.g. "androidtv", "samsungtv". Persisted on [PairedDevice.pluginId]. */
    val pluginId: String

    val displayName: String

    val supportedDeviceTypes: Set<DeviceType>

    val pairingStrategy: PairingStrategy

    /** Does this plugin own the device behind [discovered]? Used to route discovery results. */
    fun canHandle(discovered: DiscoveredDevice): Boolean

    suspend fun pair(discovered: DiscoveredDevice, input: PairingInput): PairingResult

    /** Re-establish a session for an already-paired device, e.g. on app foreground. */
    suspend fun connect(device: PairedDevice): ConnectionResult

    suspend fun disconnect(device: PairedDevice)

    /**
     * Called when the user removes this device from the app entirely (not
     * just disconnects). Default no-op: only plugins that persist their own
     * trust state outside of [PairedDevice] itself (e.g. [com.homecontrol.plugins.windows.crypto.WindowsDeviceStore]'s
     * pinned certificate) need to override this, so the stored record
     * doesn't outlive the paired device and silently reject a legitimate
     * future re-pair attempt at the same address.
     */
    suspend fun forgetDevice(device: PairedDevice) = Unit

    suspend fun getCapabilities(device: PairedDevice): DeviceCapabilities

    suspend fun sendKey(device: PairedDevice, key: RemoteKey)

    suspend fun powerOn(device: PairedDevice)

    suspend fun powerOff(device: PairedDevice)

    suspend fun volumeUp(device: PairedDevice)

    suspend fun volumeDown(device: PairedDevice)

    suspend fun mute(device: PairedDevice)

    suspend fun sendText(device: PairedDevice, text: String)

    /**
     * Default no-op bodies (not abstract): only [supportsMouse]/
     * [DeviceCapabilities.supportsTouchpad] plugins (currently just
     * Windows) override these, so adding them didn't require touching
     * every existing plugin.
     */
    suspend fun moveMouse(device: PairedDevice, deltaX: Int, deltaY: Int) = Unit

    suspend fun clickMouse(device: PairedDevice, button: MouseButton) = Unit

    suspend fun launchApp(device: PairedDevice, appId: String)

    suspend fun getInstalledApps(device: PairedDevice): List<AppInfo>

    /**
     * Adds a new launch button on the device side — currently just Windows,
     * where the phone can add a URL (it has no way to browse the PC's
     * filesystem, so only the PC's own "Manage app buttons" window can add
     * an actual .exe). Returns whether it actually succeeded. Default no-op
     * false so plugins that don't support authoring buttons at all (every
     * TV plugin) don't need to override it.
     */
    suspend fun addAppButton(device: PairedDevice, label: String, target: String): Boolean = false

    /**
     * Device-level system shortcuts distinct from installed apps — e.g. the
     * Windows key, opening a specific folder, Task Manager. Default empty:
     * only Windows populates this.
     */
    suspend fun getQuickActions(device: PairedDevice): List<AppInfo> = emptyList()

    suspend fun triggerQuickAction(device: PairedDevice, actionId: String) = Unit

    /**
     * TV/AV input sources (HDMI 1, HDMI 2, Satellite, ...) — distinct from
     * quick actions since it's only meaningful for [DeviceCapabilities.supportsInputSource]
     * devices, and the actual list of inputs is queried from the device
     * itself rather than assumed.
     */
    suspend fun getSources(device: PairedDevice): List<AppInfo> = emptyList()

    suspend fun selectSource(device: PairedDevice, sourceId: String) = Unit
}
