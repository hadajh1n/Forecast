package com.example.forecast.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ForecastWeatherDao {
    @Query("SELECT * FROM forecast_weather WHERE cityName = :cityName ORDER BY date ASC")
    suspend fun getForCity(cityName: String): List<ForecastWeatherEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entities: List<ForecastWeatherEntity>)

    @Query("DELETE FROM forecast_weather WHERE cityName = :cityName")
    suspend fun deleteForCity(cityName: String)
}