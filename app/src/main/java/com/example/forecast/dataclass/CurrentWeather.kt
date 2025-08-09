package com.example.forecast.dataclass

data class CurrentWeather(
    val name: String,   // Название города
    val main: Main
)

data class Main(
    val temp: Float    // Текущая температура
)