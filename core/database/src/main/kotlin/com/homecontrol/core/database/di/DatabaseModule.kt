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
            // Real users are now installing this app (Play Store, website
            // download) — silently dropping every paired device on a schema
            // bump is no longer acceptable the way it was during solo
            // pre-release testing. Destructive fallback is scoped to only
            // versions 1-7 (nobody real ever had those, and exportSchema is
            // still off, so there's no schema history to write real
            // migrations against for them anyway). Any future bump past the
            // current version 8 with no explicit Migration now throws
            // instead of silently wiping data — a loud failure we'll catch
            // before shipping, rather than quietly losing users' pairings.
            // (Turning exportSchema on and writing real Migration objects
            // for future bumps is the proper next step, but needs the Room
            // Gradle plugin added to the project — not done here.)
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4, 5, 6, 7)
            .build()
    }
    single<PairedDeviceDao> { get<HomeControlDatabase>().pairedDeviceDao() }
}
