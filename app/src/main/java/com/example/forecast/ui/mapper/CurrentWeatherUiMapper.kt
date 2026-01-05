package com.example.forecast.ui.mapper

import com.example.forecast.data.dataclass.current.CurrentWeatherCache
import com.example.forecast.data.dataclass.current.CurrentWeatherUI

class CurrentWeatherUiMapper {
    fun map(cache: CurrentWeatherCache): CurrentWeatherUI =
        CurrentWeatherUI(
            cityName = cache.cityName,
            temp = cache.temp,
            iconUrl = "https://openweathermap.org/img/wn/${cache.icon}@2x.png"
        )
}