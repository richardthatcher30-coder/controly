package com.homecontrol.plugins.registry

import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.core.pluginapi.PluginRegistry

/**
 * The only class in the app that depends on every concrete plugin module —
 * add a new device plugin by adding one `single<IDevicePlugin>(named(...))`
 * binding in that plugin's own Koin module and one dependency line in
 * `pluginRegistryModule`, nothing else changes.
 */
class PluginRegistryImpl(
    private val devicePlugins: Set<IDevicePlugin>,
) : PluginRegistry {

    override val plugins: Set<IDevicePlugin> = devicePlugins

    override fun findPluginFor(discovered: DiscoveredDevice): IDevicePlugin? =
        plugins.firstOrNull { it.canHandle(discovered) }

    override fun findPluginById(pluginId: String): IDevicePlugin? =
        plugins.firstOrNull { it.pluginId == pluginId }
}
