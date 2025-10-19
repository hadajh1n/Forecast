package com.example.forecast.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val cityName: String,
    val orderIndex: Int,
    val isActive: Boolean = true,
    val lastUpdated: Long = 0L
)