package com.example.forecast.data.dataclass.current

data class CurrentWeatherDTO(
    val name: String,
    val main: CurrentMainDTO,
    val weather: List<WeatherDTO>
)

data class CurrentMainDTO(
    val temp: Float
)

data class WeatherDTO(
    val icon: String
)