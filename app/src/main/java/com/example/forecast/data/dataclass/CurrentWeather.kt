package com.example.forecast.data.dataclass

data class CurrentWeather(
    val wind: Wind,
    val name: String,
    val main: Main,
    val weather: List<Weather>
)

data class Main(
    val temp: Float
)

data class Weather(
    val icon: String,
    val description: String
)

data class Wind(
    val speed: Float,
    val deg: Int
)