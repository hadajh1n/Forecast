package com.example.forecast.data.room.forecast

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forecast_weather",
    indices = [Index(value = ["cityName"])]
)
data class ForecastWeatherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityName: String,
    val dt: Long,
    val tempMax: Float,
    val tempMin: Float,
    val icon: String,
    val wind: Float? = null,
    val rain: Float? = null,
    val snow: Float? = null,
    val timestamp: Long = 0L
)