package com.example.forecast.data.mapper.current

import com.example.forecast.data.dataclass.current.CurrentWeatherCache
import com.example.forecast.data.room.current.CurrentWeatherEntity

class CurrentWeatherCacheEntityMapper {
    fun fromCacheToEntity(cache: CurrentWeatherCache, orderIndex: Int): CurrentWeatherEntity =
        CurrentWeatherEntity(
            cityName = cache.cityName,
            temp = cache.temp,
            icon = cache.icon,
            timestamp = cache.timestamp,
            orderIndex = orderIndex
        )
}