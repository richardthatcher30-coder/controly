package com.homecontrol.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PairedDeviceEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class HomeControlDatabase : RoomDatabase() {
    abstract fun pairedDeviceDao(): PairedDeviceDao
}
