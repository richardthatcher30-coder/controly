package com.homecontrol.core.data.di

import com.homecontrol.core.data.PairedDeviceRepository
import com.homecontrol.core.data.PairedDeviceRepositoryImpl
import com.homecontrol.core.data.RemoteControlRepository
import com.homecontrol.core.data.RemoteControlRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindPairedDeviceRepository(impl: PairedDeviceRepositoryImpl): PairedDeviceRepository

    @Binds
    abstract fun bindRemoteControlRepository(impl: RemoteControlRepositoryImpl): RemoteControlRepository
}
