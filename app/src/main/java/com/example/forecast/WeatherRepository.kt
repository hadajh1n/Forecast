package com.example.forecast

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastWeather

object WeatherRepository {

    data class CachedWeatherData(
        val current: CurrentWeather? = null,
        val forecast: ForecastWeather? = null,
        val timestampCurrent: Long = 0L,
        val timestampForecast: Long = 0L
    )

    private var cachedDetails = mutableMapOf<String, CachedWeatherData>()

    private val _cachedWeatherLiveData =
        MutableLiveData<Map<String, CachedWeatherData>>(cachedDetails)
    val cachedWeatherLiveData:
            LiveData<Map<String, CachedWeatherData>> get() = _cachedWeatherLiveData

    fun getTimestampCurrent(cityName: String): Long? = cachedDetails[cityName]?.timestampCurrent
    fun getTimestampForecast(cityName: String): Long? = cachedDetails[cityName]?.timestampForecast

    fun getCachedDetails(cityName: String): CachedWeatherData? = cachedDetails[cityName]

    fun setCachedCurrent(cityName: String, current: CurrentWeather, timestamp: Long) {
        val existing = cachedDetails[cityName]
        cachedDetails[cityName] = CachedWeatherData(
            current = current,
            forecast = existing?.forecast,
            timestampCurrent = timestamp,
            timestampForecast = existing?.timestampForecast ?: 0L
        )
        _cachedWeatherLiveData.value = cachedDetails
    }

    fun setCachedForecast(cityName: String, forecast: ForecastWeather, timestamp: Long) {
        val existing = cachedDetails[cityName]
        cachedDetails[cityName] = CachedWeatherData(
            current = existing?.current,
            forecast = forecast,
            timestampCurrent = existing?.timestampCurrent ?: 0L,
            timestampForecast = timestamp
        )
        _cachedWeatherLiveData.value = cachedDetails
    }

    fun removeCity(cityName: String) {
        cachedDetails.remove(cityName)
        _cachedWeatherLiveData.value = cachedDetails
    }
}