package com.example.forecast.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CityEntity::class, CurrentWeatherEntity::class, ForecastWeatherEntity::class],
    version = 2,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
    abstract fun currentWeatherDao(): CurrentWeatherDao
    abstract fun forecastWeatherDao(): ForecastWeatherDao
}