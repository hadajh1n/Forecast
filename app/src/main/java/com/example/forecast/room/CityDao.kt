package com.example.forecast.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CityDao {
    @Query("SELECT * FROM cities WHERE isActive = 1 ORDER BY orderIndex ASC")
    suspend fun getActiveCities(): List<CityEntity>

    @Query("SELECT MAX(orderIndex) FROM cities")
    suspend fun getMaxOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CityEntity)

    @Query("UPDATE cities SET isActive = 0, lastUpdated = :timestamp WHERE cityName = :cityName")
    suspend fun softDelete(cityName: String, timestamp: Long)
}