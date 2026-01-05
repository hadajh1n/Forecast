package com.example.forecast.data.mapper.forecast

import com.example.forecast.data.dataclass.forecast.ForecastItemCache
import com.example.forecast.data.dataclass.forecast.ForecastWeatherCache
import com.example.forecast.data.room.forecast.ForecastWeatherEntity

class ForecastWeatherEntityCacheMapper {
    fun fromEntityToCache(
        cityName: String,
        entities: List<ForecastWeatherEntity>
    ): ForecastWeatherCache {
        return ForecastWeatherCache(
            cityName = cityName,
            items = entities.map {
                ForecastItemCache(
                    dt = it.dt,
                    tempMax = it.tempMax,
                    tempMin = it.tempMin,
                    icon = it.icon
                )
            },
            timestamp = entities.maxOfOrNull { it.timestamp } ?: 0L
        )
    }
}