package com.example.forecast.data.dataclass.forecast

import com.example.forecast.data.dataclass.current.WeatherDTO

data class ForecastWeatherDTO(
    val list: List<ForecastItemDTO>
)

data class ForecastItemDTO(
    val dt: Long,
    val main: ForecastMainDTO,
    val weather: List<WeatherDTO>,
    val wind: WindDTO? = null,
    val rain: RainDTO? = null,
    val snow: SnowDTO? = null
)

data class ForecastMainDTO(
    val temp_max: Float,
    val temp_min: Float
)

data class WindDTO(val speed: Float? = null)

data class RainDTO(val `3h`: Float? = null)

data class SnowDTO(val `3h`: Float? = null)