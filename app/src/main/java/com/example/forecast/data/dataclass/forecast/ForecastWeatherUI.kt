package com.example.forecast.data.dataclass.forecast

data class ForecastWeatherUI(
    val dayOfWeek: String,
    val iconUrl: String,
    val tempMax: Float,
    val tempMin: Float
)