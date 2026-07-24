package com.homecontrol.plugins.sonytv.di

import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.plugins.sonytv.SonyDeviceStore
import com.homecontrol.plugins.sonytv.SonyTvPlugin
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sonyTvModule = module {
    single { SonyDeviceStore(androidContext()) }
    single<IDevicePlugin>(named("sonytv")) { SonyTvPlugin(get()) }
}
