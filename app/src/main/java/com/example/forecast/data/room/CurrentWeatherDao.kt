package com.example.forecast.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CurrentWeatherDao {
    @Query("SELECT * FROM current_weather WHERE cityName = :cityName")
    suspend fun getForCity(cityName: String): CurrentWeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CurrentWeatherEntity)
}