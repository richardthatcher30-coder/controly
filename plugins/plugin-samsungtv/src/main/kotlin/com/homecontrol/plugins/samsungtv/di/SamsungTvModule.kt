package com.homecontrol.plugins.samsungtv.di

import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.plugins.samsungtv.SamsungClientIdentity
import com.homecontrol.plugins.samsungtv.SamsungTvPlugin
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val samsungTvModule = module {
    single { SamsungClientIdentity(androidContext()) }
    single<IDevicePlugin>(named("samsungtv")) { SamsungTvPlugin(get()) }
}
