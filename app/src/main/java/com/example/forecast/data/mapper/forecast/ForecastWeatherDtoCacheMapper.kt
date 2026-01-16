package com.example.forecast.data.mapper.forecast

import com.example.forecast.data.dataclass.forecast.ForecastItemCache
import com.example.forecast.data.dataclass.forecast.ForecastWeatherCache
import com.example.forecast.data.dataclass.forecast.ForecastWeatherDTO

class ForecastWeatherDtoCacheMapper {
    fun fromDtoToCache(cityName: String, dto: ForecastWeatherDTO): ForecastWeatherCache =
        ForecastWeatherCache(
            cityName = cityName,
            items = dto.list.map {
                ForecastItemCache(
                    dt = it.dt,
                    tempMax = it.main.temp_max,
                    tempMin = it.main.temp_min,
                    icon = it.weather.firstOrNull()?.icon.orEmpty(),
                    wind = null,
                    rain = null,
                    snow = null
                )
            },
            timestamp = System.currentTimeMillis()
        )
}