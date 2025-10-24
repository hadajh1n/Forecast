package com.example.forecast.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forecast_weather",
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["cityName"],
            childColumns = ["cityName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cityName", "date"], unique = true)]
)
data class ForecastWeatherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityName: String,
    val date: Long,
    val temp: Float,
    val tempMax: Float,
    val tempMin: Float,
    val icon: String,
    val timestamp: Long
)