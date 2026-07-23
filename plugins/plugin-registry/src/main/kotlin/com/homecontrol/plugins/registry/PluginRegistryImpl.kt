package com.homecontrol.plugins.registry

import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.core.pluginapi.PluginRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class in the app that depends on every concrete plugin module —
 * add a new device plugin by adding one `@Binds @IntoSet` in
 * [PluginRegistryModule] and one dependency line here, nothing else changes.
 */
@Singleton
class PluginRegistryImpl @Inject constructor(
    private val devicePlugins: Set<@JvmSuppressWildcards IDevicePlugin>,
) : PluginRegistry {

    override val plugins: Set<IDevicePlugin> = devicePlugins

    override fun findPluginFor(discovered: DiscoveredDevice): IDevicePlugin? =
        plugins.firstOrNull { it.canHandle(discovered) }

    override fun findPluginById(pluginId: String): IDevicePlugin? =
        plugins.firstOrNull { it.pluginId == pluginId }
}
