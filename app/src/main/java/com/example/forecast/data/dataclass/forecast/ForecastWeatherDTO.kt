package com.example.forecast.data.dataclass.forecast

import com.example.forecast.data.dataclass.current.WeatherDTO

data class ForecastWeatherDTO(
    val list: List<ForecastItemDTO>
)

data class ForecastItemDTO(
    val dt: Long,
    val main: ForecastMainDTO,
    val weather: List<WeatherDTO>
)

data class ForecastMainDTO(
    val temp_max: Float,
    val temp_min: Float
)