package com.example.forecast

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastWeather
import com.example.forecast.room.AppDatabase
import com.example.forecast.room.WeatherApp
import com.example.forecast.room.WeatherCacheEntity
import com.google.gson.Gson

object WeatherRepository {

    data class CachedWeatherData(
        val current: CurrentWeather? = null,
        val forecast: ForecastWeather? = null,
        val timestampCurrent: Long = 0L,
        val timestampForecast: Long = 0L,
        val orderIndex: Int = 0
    )

    private var cachedDetails = mutableMapOf<String, CachedWeatherData>()
    private val gson = Gson()

    private val _cachedWeatherLiveData =
        MutableLiveData<Map<String, CachedWeatherData>>(cachedDetails)
    val cachedWeatherLiveData:
            LiveData<Map<String, CachedWeatherData>> get() = _cachedWeatherLiveData

    private val db by lazy {
        Room.databaseBuilder(
            WeatherApp.instance.applicationContext,
            AppDatabase::class.java,
            "weather-db"
        ).build()
    }

    suspend fun getCities(): List<String> {
        return db.weatherDao().getAll().map { it.cityName }
    }

    fun getTimestampCurrent(cityName: String): Long? = cachedDetails[cityName]?.timestampCurrent
    fun getTimestampForecast(cityName: String): Long? = cachedDetails[cityName]?.timestampForecast

    suspend fun getCachedDetails(cityName: String): CachedWeatherData? {
        if (!cachedDetails.containsKey(cityName)) {
            loadFromDatabase(cityName)
        }
        return cachedDetails[cityName]
    }

    private suspend fun loadFromDatabase(cityName: String) {
        val entity = db.weatherDao().getForCity(cityName) ?: return
        val current = entity.currentJson?.let { gson.fromJson(it, CurrentWeather::class.java) }
        val forecast = entity.forecastJson?.let { gson.fromJson(it, ForecastWeather::class.java) }
        cachedDetails[cityName] = CachedWeatherData(
            current = current,
            forecast = forecast,
            timestampCurrent = entity.timestampCurrent,
            timestampForecast = entity.timestampForecast,
            orderIndex = entity.orderIndex
        )
        _cachedWeatherLiveData.value = cachedDetails
    }

    suspend fun setCachedCurrent(cityName: String, current: CurrentWeather, timestamp: Long) {
        val existing = getCachedDetails(cityName) ?: CachedWeatherData()
        var orderIndex = existing.orderIndex
        if (orderIndex == 0) {
            orderIndex = (db.weatherDao().getMaxOrderIndex() ?: 0) + 1
        }

        val newData = CachedWeatherData(
            current = current,
            forecast = existing.forecast,
            timestampCurrent = timestamp,
            timestampForecast = existing.timestampForecast,
            orderIndex = orderIndex
        )
        cachedDetails[cityName] = newData
        _cachedWeatherLiveData.value = cachedDetails

        val entity = WeatherCacheEntity(
            cityName = cityName,
            currentJson = gson.toJson(current),
            forecastJson = gson.toJson(existing.forecast),
            timestampCurrent = timestamp,
            timestampForecast = existing.timestampForecast,
            orderIndex = orderIndex
        )
        db.weatherDao().insert(entity)
    }

    suspend fun setCachedForecast(cityName: String, forecast: ForecastWeather, timestamp: Long) {
        val existing = getCachedDetails(cityName) ?: CachedWeatherData()
        var orderIndex = existing.orderIndex
        if (orderIndex == 0) {
            orderIndex = (db.weatherDao().getMaxOrderIndex() ?: 0) + 1
        }

        val newData = CachedWeatherData(
            current = existing.current,
            forecast = forecast,
            timestampCurrent = existing.timestampCurrent,
            timestampForecast = timestamp,
            orderIndex = orderIndex
        )
        cachedDetails[cityName] = newData
        _cachedWeatherLiveData.value = cachedDetails

        val entity = WeatherCacheEntity(
            cityName = cityName,
            currentJson = gson.toJson(existing.current),
            forecastJson = gson.toJson(forecast),
            timestampCurrent = existing.timestampCurrent,
            timestampForecast = timestamp,
            orderIndex = orderIndex
        )
        db.weatherDao().insert(entity)
    }

    suspend fun removeCity(cityName: String) {
        cachedDetails.remove(cityName)
        _cachedWeatherLiveData.value = cachedDetails

        val entity = db.weatherDao().getForCity(cityName)
        if (entity != null) {
            db.weatherDao().delete(entity)
        }
    }
}