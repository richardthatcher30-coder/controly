package com.homecontrol.plugins.windows.di

import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.plugins.windows.WindowsPlugin
import com.homecontrol.plugins.windows.crypto.CompanionKeyStore
import com.homecontrol.plugins.windows.crypto.WindowsDeviceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val windowsModule = module {
    single { CompanionKeyStore(androidContext(), get()) }
    single { WindowsDeviceStore(androidContext()) }
    single<IDevicePlugin>(named("windows")) { WindowsPlugin(get(), get()) }
}
