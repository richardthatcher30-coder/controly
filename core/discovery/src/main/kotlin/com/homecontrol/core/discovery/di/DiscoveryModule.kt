package com.homecontrol.core.discovery.di

import com.homecontrol.core.discovery.DeviceDiscoveryScanner
import com.homecontrol.core.discovery.NetworkDeviceDiscoveryScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DiscoveryModule {

    @Binds
    abstract fun bindDeviceDiscoveryScanner(impl: NetworkDeviceDiscoveryScanner): DeviceDiscoveryScanner
}
