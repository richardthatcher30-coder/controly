package com.homecontrol.plugins.androidtv.di

import com.homecontrol.core.pluginapi.IDevicePlugin
import com.homecontrol.plugins.androidtv.AndroidTvPlugin
import com.homecontrol.plugins.androidtv.adb.AdbKeyStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidTvModule = module {
    single { AdbKeyStore(androidContext(), get()) }
    single<IDevicePlugin>(named("androidtv")) { AndroidTvPlugin(get()) }
}
