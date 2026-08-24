package com.example.monitoreotp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OfflineLocationDao {
    @Insert
    suspend fun insert(location: OfflineLocation)

    @Query("SELECT * FROM offline_locations ORDER BY createdAt ASC")
    suspend fun getAll(): List<OfflineLocation>

    @Delete
    suspend fun delete(location: OfflineLocation)

    @Query("DELETE FROM offline_locations WHERE createdAt < :maxAge")
    suspend fun deleteOld(maxAge: Long)

    @Query("SELECT COUNT(*) FROM offline_locations")
    suspend fun getCount(): Int
}