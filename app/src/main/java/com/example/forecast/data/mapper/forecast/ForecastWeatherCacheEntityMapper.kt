package com.example.forecast.data.mapper.forecast

import com.example.forecast.data.dataclass.forecast.ForecastItemCache
import com.example.forecast.data.room.forecast.ForecastWeatherEntity

class ForecastWeatherCacheEntityMapper {
    fun fromCacheToEntity(
        cityName: String,
        item: ForecastItemCache,
        timestamp: Long
    ): ForecastWeatherEntity =
        ForecastWeatherEntity(
            cityName = cityName,
            dt = item.dt,
            tempMax = item.tempMax,
            tempMin = item.tempMin,
            icon = item.icon,
            wind = null,
            rain = null,
            snow = null,
            timestamp = timestamp
        )
}