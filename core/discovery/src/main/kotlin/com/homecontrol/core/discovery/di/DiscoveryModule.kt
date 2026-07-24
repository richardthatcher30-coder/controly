package com.homecontrol.core.discovery.di

import com.homecontrol.core.discovery.DeviceDiscoveryScanner
import com.homecontrol.core.discovery.NetworkDeviceDiscoveryScanner
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val discoveryModule = module {
    single<DeviceDiscoveryScanner> { NetworkDeviceDiscoveryScanner(androidContext()) }
}
