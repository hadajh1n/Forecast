package com.example.forecast.data.dataclass.current

data class CurrentWeatherCache(
    val cityName: String,
    val temp: Float,
    val icon: String,
    val timestamp: Long
)