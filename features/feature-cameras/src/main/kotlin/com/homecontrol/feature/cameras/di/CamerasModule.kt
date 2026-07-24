package com.homecontrol.feature.cameras.di

import com.homecontrol.feature.cameras.CameraGridStore
import com.homecontrol.feature.cameras.CameraGridViewModel
import com.homecontrol.feature.cameras.CameraStore
import com.homecontrol.feature.cameras.CameraViewModel
import com.homecontrol.feature.cameras.CamerasViewModel
import com.homecontrol.feature.cameras.onvif.OnvifDiscoveryScanner
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val camerasModule = module {
    single { CameraStore(androidContext()) }
    single { CameraGridStore(androidContext()) }
    single { OnvifDiscoveryScanner() }
    viewModel { CameraViewModel(get(), get()) }
    viewModel { CamerasViewModel(get(), get()) }
    viewModel { CameraGridViewModel(get(), get()) }
}
