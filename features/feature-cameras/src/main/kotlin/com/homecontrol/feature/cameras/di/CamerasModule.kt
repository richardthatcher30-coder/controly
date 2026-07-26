package com.homecontrol.feature.cameras.di

import com.homecontrol.feature.cameras.CameraGridStore
import com.homecontrol.feature.cameras.CameraGridViewModel
import com.homecontrol.feature.cameras.CameraStore
import com.homecontrol.feature.cameras.CameraViewModel
import com.homecontrol.feature.cameras.CamerasViewModel
import com.homecontrol.feature.cameras.LocalNetworkChecker
import com.homecontrol.feature.cameras.onvif.OnvifDiscoveryScanner
import com.homecontrol.feature.cameras.upnp.UpnpPortForwarder
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val camerasModule = module {
    single { CameraStore(androidContext()) }
    single { CameraGridStore(androidContext()) }
    single { OnvifDiscoveryScanner() }
    single { UpnpPortForwarder(androidContext()) }
    single { LocalNetworkChecker(androidContext()) }
    viewModel { CameraViewModel(get(), get(), get()) }
    viewModel { CamerasViewModel(get(), get(), get()) }
    viewModel { CameraGridViewModel(get(), get()) }
}
