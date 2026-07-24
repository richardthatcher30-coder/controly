package com.homecontrol.app

import android.app.Application
import com.homecontrol.core.data.di.dataModule
import com.homecontrol.core.database.di.databaseModule
import com.homecontrol.core.discovery.di.discoveryModule
import com.homecontrol.core.security.di.securityModule
import com.homecontrol.feature.cameras.di.camerasModule
import com.homecontrol.feature.dashboard.di.dashboardModule
import com.homecontrol.feature.devices.di.devicesModule
import com.homecontrol.feature.remote.di.remoteModule
import com.homecontrol.plugins.androidtv.di.androidTvModule
import com.homecontrol.plugins.registry.di.pluginRegistryModule
import com.homecontrol.plugins.samsungtv.di.samsungTvModule
import com.homecontrol.plugins.sonytv.di.sonyTvModule
import com.homecontrol.plugins.windows.di.windowsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class HomeControlApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@HomeControlApplication)
            modules(
                securityModule,
                databaseModule,
                dataModule,
                discoveryModule,
                androidTvModule,
                samsungTvModule,
                sonyTvModule,
                windowsModule,
                pluginRegistryModule,
                dashboardModule,
                devicesModule,
                remoteModule,
                camerasModule,
            )
        }
    }
}
