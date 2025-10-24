package com.example.forecast.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "current_weather",
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["cityName"],
            childColumns = ["cityName"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)

data class CurrentWeatherEntity(
    @PrimaryKey val cityName: String,
    val temp: Float,
    val icon: String,
    val timestamp: Long
)