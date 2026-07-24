package com.homecontrol.feature.remote.di

import com.homecontrol.feature.remote.RemoteViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val remoteModule = module {
    viewModel { RemoteViewModel(get(), get(), get()) }
}
