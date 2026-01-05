package com.example.forecast.data.room.current

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CurrentWeatherDao {
    @Query("SELECT * FROM current_weather ORDER BY orderIndex ASC")
    suspend fun getAllCities(): List<CurrentWeatherEntity>

    @Query("SELECT MAX(orderIndex) FROM current_weather")
    suspend fun getMaxIndex(): Int?

    @Query("SELECT * FROM current_weather WHERE cityName = :cityName")
    suspend fun getForCityCurrent(cityName: String): CurrentWeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrent(entity: CurrentWeatherEntity)

    @Query("DELETE FROM current_weather WHERE cityName = :cityName")
    suspend fun deleteForCityCurrent(cityName: String)
}