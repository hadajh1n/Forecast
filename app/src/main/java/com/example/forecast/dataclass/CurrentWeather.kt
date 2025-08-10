package com.example.forecast.dataclass

data class CurrentWeather(
    val name: String,           // Название города
    val main: Main,
    val weather: List<Weather>  // Список погодных условий
)

data class Main(
    val temp: Float             // Текущая температура
)

data class Weather(
    val icon: String            // Код иконки погоды
)