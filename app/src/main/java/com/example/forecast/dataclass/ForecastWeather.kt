package com.example.forecast.dataclass

data class ForecastWeather(
    val list: List<ForecastItem>
)

data class ForecastItem(
    val dt: Long,
    val main: ForecastMain,
    val weather: List<Weather>
)

data class ForecastMain(
    val temp: Float,
    val tempMax: Float,
    val tempMin: Float
)