package com.example.forecast.data.dataclass.forecast

data class ForecastWeatherCache(
    val cityName: String,
    val items: List<ForecastItemCache>,
    val timestamp: Long
)

data class ForecastItemCache(
    val dt: Long,
    val tempMax: Float,
    val tempMin: Float,
    val icon: String,
    val wind: Float?,
    val rain: Float?,
    val snow: Float?
)