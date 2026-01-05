package com.example.forecast.data.mapper.current

import com.example.forecast.data.dataclass.current.CurrentWeatherCache
import com.example.forecast.data.dataclass.current.CurrentWeatherDTO

class CurrentWeatherDtoCacheMapper {
    fun fromDtoToCache(dto: CurrentWeatherDTO): CurrentWeatherCache =
        CurrentWeatherCache(
            cityName = dto.name,
            temp = dto.main.temp,
            icon = dto.weather.firstOrNull()?.icon.orEmpty(),
            timestamp = System.currentTimeMillis()
        )
}