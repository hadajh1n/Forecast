package com.example.forecast

import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastWeather

object WeatherRepository {

    data class CachedCityData(
        val cities: List<CurrentWeather>,
        val timestamp: Long
    )

    data class CachedWeatherData(
        val weather: CurrentWeather,
        val forecast: ForecastWeather,
        val timestamp: Long
    )

    private var cachedCities: CachedCityData? = null
    private var cachedDetails = mutableMapOf<String, CachedWeatherData>()

    fun getCachedCities(): CachedCityData? = cachedCities

    fun setCachedCities(cities: List<CurrentWeather>, timestamp: Long) {
        cachedCities = CachedCityData(cities, timestamp)
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
        cachedCities?.let {
            val updatedCities = it.cities.filter { city ->
                !city.name.equals(cityName, ignoreCase = true)
            }
            cachedCities = CachedCityData(updatedCities, System.currentTimeMillis())
        }
        cachedDetails.remove(cityName)
    }
}