package com.example.forecast.data.mapper.current

import com.example.forecast.data.dataclass.current.CurrentWeatherCache
import com.example.forecast.data.room.current.CurrentWeatherEntity

class CurrentWeatherEntityCacheMapper {
    fun fromEntityToCache(entity: CurrentWeatherEntity): CurrentWeatherCache =
        CurrentWeatherCache(
            cityName = entity.cityName,
            temp = entity.temp,
            icon = entity.icon,
            timestamp = entity.timestamp
        )
}