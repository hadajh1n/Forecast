package com.example.forecast

import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastWeather

object WeatherRepository {

    data class CachedWeatherData(
        val weather: CurrentWeather,
        val forecast: ForecastWeather? = null,
        val timestamp: Long
    )

    private var cachedDetails = mutableMapOf<String, CachedWeatherData>()

    fun getCityTimestamp(cityName: String): Long? = cachedDetails[cityName]?.timestamp

    fun setCachedCurrentWeather(cityName: String, weather: CurrentWeather, timestamp: Long) {
        val existing = cachedDetails[cityName]
        cachedDetails[cityName] = CachedWeatherData(
            weather = weather,
            forecast = existing?.forecast,
            timestamp = timestamp
        )
    }

    fun getCachedDetails(cityName: String): CachedWeatherData? = cachedDetails[cityName]

    fun setCachedDetails(
        cityName: String,
        weather: CurrentWeather,
        forecast: ForecastWeather,
        timestamp: Long
    ) {
        cachedDetails[cityName] = CachedWeatherData(weather, forecast, timestamp)
    }

    fun removeCity(cityName: String) {
        cachedDetails.remove(cityName)
    }
}