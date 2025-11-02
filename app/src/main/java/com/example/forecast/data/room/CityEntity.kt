package com.example.forecast.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val cityName: String,
    val orderIndex: Int,
    val lastUpdated: Long = 0L
)