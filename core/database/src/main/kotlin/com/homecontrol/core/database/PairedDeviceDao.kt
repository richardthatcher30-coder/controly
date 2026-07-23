package com.homecontrol.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {

    @Upsert
    suspend fun upsert(device: PairedDeviceEntity)

    @Query("SELECT * FROM paired_devices ORDER BY name ASC")
    fun observeAll(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE id = :id")
    suspend fun getById(id: String): PairedDeviceEntity?

    @Delete
    suspend fun delete(device: PairedDeviceEntity)
}
