package com.homecontrol.plugins.registry.di

import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.core.pluginapi.PluginRegistry
import com.homecontrol.plugins.androidtv.AndroidTvPlugin
import com.homecontrol.plugins.registry.PluginRegistryImpl
import com.homecontrol.plugins.samsungtv.SamsungTvPlugin
import com.homecontrol.plugins.sonytv.SonyTvPlugin
import com.homecontrol.plugins.windows.WindowsPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginRegistryModule {

    @Binds
    abstract fun bindPluginRegistry(impl: PluginRegistryImpl): PluginRegistry

    @Binds
    @IntoSet
    abstract fun bindAndroidTvPlugin(plugin: AndroidTvPlugin): IDevicePlugin

    @Binds
    @IntoSet
    abstract fun bindWindowsPlugin(plugin: WindowsPlugin): IDevicePlugin

    @Binds
    @IntoSet
    abstract fun bindSonyTvPlugin(plugin: SonyTvPlugin): IDevicePlugin

    @Binds
    @IntoSet
    abstract fun bindSamsungTvPlugin(plugin: SamsungTvPlugin): IDevicePlugin
}
