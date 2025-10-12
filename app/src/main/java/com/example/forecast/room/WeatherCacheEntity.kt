package com.example.forecast.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val cityName: String,
    val currentJson: String? = null,
    val forecastJson: String? = null,
    val timestampCurrent: Long = 0L,
    val timestampForecast: Long = 0L,
    val orderIndex: Int
)