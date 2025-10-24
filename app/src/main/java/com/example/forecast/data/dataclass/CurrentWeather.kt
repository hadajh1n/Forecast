package com.example.forecast.data.dataclass

data class CurrentWeather(
    val name: String,
    val main: Main,
    val weather: List<Weather>
)

data class Main(
    val temp: Float
)

data class Weather(
    val icon: String
)