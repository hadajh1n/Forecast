package com.example.forecast.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.forecast.data.room.current.CurrentWeatherDao
import com.example.forecast.data.room.current.CurrentWeatherEntity
import com.example.forecast.data.room.forecast.ForecastWeatherDao
import com.example.forecast.data.room.forecast.ForecastWeatherEntity

@Database(
    entities = [CurrentWeatherEntity::class, ForecastWeatherEntity::class],
    version = 2,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun currentWeatherDao(): CurrentWeatherDao
    abstract fun forecastWeatherDao(): ForecastWeatherDao
}