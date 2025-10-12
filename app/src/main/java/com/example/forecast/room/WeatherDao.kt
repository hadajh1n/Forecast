package com.example.forecast.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache ORDER BY orderIndex ASC")
    suspend fun getAll(): List<WeatherCacheEntity>

    @Query("SELECT * FROM weather_cache WHERE cityName = :cityName")
    suspend fun getForCity(cityName: String): WeatherCacheEntity?

    @Query("SELECT MAX(orderIndex) FROM weather_cache")
    suspend fun getMaxOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WeatherCacheEntity)

    @Delete
    suspend fun delete(entity: WeatherCacheEntity)
}