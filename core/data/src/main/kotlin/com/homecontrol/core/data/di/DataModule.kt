package com.homecontrol.core.data.di

import com.homecontrol.core.data.PairedDeviceRepository
import com.homecontrol.core.data.PairedDeviceRepositoryImpl
import com.homecontrol.core.data.RemoteControlRepository
import com.homecontrol.core.data.RemoteControlRepositoryImpl
import org.koin.dsl.module

val dataModule = module {
    single<PairedDeviceRepository> { PairedDeviceRepositoryImpl(get(), get()) }
    single<RemoteControlRepository> { RemoteControlRepositoryImpl(get()) }
}
