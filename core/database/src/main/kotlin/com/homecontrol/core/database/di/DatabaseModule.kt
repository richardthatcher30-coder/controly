package com.homecontrol.core.database.di

import android.content.Context
import androidx.room.Room
import com.homecontrol.core.database.HomeControlDatabase
import com.homecontrol.core.database.PairedDeviceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATABASE_NAME = "homecontrol.db"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomeControlDatabase =
        Room.databaseBuilder(context, HomeControlDatabase::class.java, DATABASE_NAME)
            // Pre-release, single-tester schema — no migrations written yet,
            // so a version bump just drops and recreates rather than crashing.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providePairedDeviceDao(database: HomeControlDatabase): PairedDeviceDao = database.pairedDeviceDao()
}
