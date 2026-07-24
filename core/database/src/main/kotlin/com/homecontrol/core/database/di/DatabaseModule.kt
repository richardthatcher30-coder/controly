package com.homecontrol.core.database.di

import androidx.room.Room
import com.homecontrol.core.database.HomeControlDatabase
import com.homecontrol.core.database.PairedDeviceDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DATABASE_NAME = "homecontrol.db"

val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), HomeControlDatabase::class.java, DATABASE_NAME)
            // Pre-release, single-tester schema — no migrations written yet,
            // so a version bump just drops and recreates rather than crashing.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single<PairedDeviceDao> { get<HomeControlDatabase>().pairedDeviceDao() }
}
