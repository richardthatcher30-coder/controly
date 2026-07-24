package com.homecontrol.core.pluginapi

import com.homecontrol.core.model.DiscoveredDevice

/**
 * Aggregates every [IDevicePlugin] the app knows about. The concrete
 * implementation lives in `plugins:plugin-registry`, backed by Koin's
 * `getAll<IDevicePlugin>()` collecting one binding per plugin module — this
 * interface is what `core:data` depends on instead, so the data layer never
 * needs a dependency on any individual plugin module.
 */
interface PluginRegistry {

    val plugins: Set<IDevicePlugin>

    /** First plugin (in registration order) that claims [discovered], or null if none does. */
    fun findPluginFor(discovered: DiscoveredDevice): IDevicePlugin?

    fun findPluginById(pluginId: String): IDevicePlugin?
}
