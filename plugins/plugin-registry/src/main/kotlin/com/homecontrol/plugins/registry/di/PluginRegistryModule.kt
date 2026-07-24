package com.homecontrol.plugins.registry.di

import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.core.pluginapi.PluginRegistry
import com.homecontrol.plugins.registry.PluginRegistryImpl
import org.koin.dsl.module

/**
 * `getAll<IDevicePlugin>()` collects every `single<IDevicePlugin>(named(...))`
 * binding registered across all the plugin modules (`androidTvModule`,
 * `windowsModule`, `sonyTvModule`, `samsungTvModule`) — Koin's direct
 * equivalent of Dagger's `@Binds @IntoSet` multibinding this module used to
 * rely on.
 */
val pluginRegistryModule = module {
    single<PluginRegistry> { PluginRegistryImpl(getAll<IDevicePlugin>().toSet()) }
}
