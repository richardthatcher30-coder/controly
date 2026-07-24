package com.homecontrol.feature.devices.di

import com.homecontrol.feature.devices.DevicesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val devicesModule = module {
    viewModel { DevicesViewModel(get(), get()) }
}
