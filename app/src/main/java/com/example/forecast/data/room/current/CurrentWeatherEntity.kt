package com.example.forecast.data.room.current

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "current_weather",
    indices = [Index(value = ["orderIndex"], unique = true)]
)
data class CurrentWeatherEntity(
    @PrimaryKey val cityName: String,
    val temp: Float,
    val icon: String,
    val timestamp: Long,
    val orderIndex: Int
)