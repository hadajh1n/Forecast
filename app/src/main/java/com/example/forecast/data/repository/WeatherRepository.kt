package com.example.forecast.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import com.example.forecast.core.app.WeatherApp
import com.example.forecast.data.dataclass.CurrentWeather
import com.example.forecast.data.dataclass.ForecastItem
import com.example.forecast.data.dataclass.ForecastMain
import com.example.forecast.data.dataclass.ForecastWeather
import com.example.forecast.data.dataclass.Main
import com.example.forecast.data.dataclass.Weather
import com.example.forecast.data.room.AppDatabase
import com.example.forecast.data.room.CityEntity
import com.example.forecast.data.room.CurrentWeatherEntity
import com.example.forecast.data.room.ForecastWeatherEntity

object WeatherRepository {

    data class CachedWeatherData(
        val current: CurrentWeather? = null,
        val forecast: ForecastWeather? = null,
        val timestampCurrent: Long = 0L,
        val timestampForecast: Long = 0L,
        val orderIndex: Int = 0
    )

    private var cachedDetails = mutableMapOf<String, CachedWeatherData>()

    private val _cachedWeatherLiveData =
        MutableLiveData<Map<String, CachedWeatherData>>(cachedDetails)
    val cachedWeatherLiveData:
            LiveData<Map<String, CachedWeatherData>>
        get() = _cachedWeatherLiveData

    private val db by lazy {
        Room.databaseBuilder(
            WeatherApp.Companion.instance.applicationContext,
            AppDatabase::class.java,
            "weather-db"
        ).build()
    }

    suspend fun getCities(): List<String> {
        val cities = db.cityDao().getActiveCities()
        cities.forEach { city ->
            if (!cachedDetails.containsKey(city.cityName)) {
                cachedDetails[city.cityName] = CachedWeatherData(
                    orderIndex = city.orderIndex,
                    timestampCurrent = 0L,
                    timestampForecast = 0L
                )
            }
        }

        _cachedWeatherLiveData.postValue(cachedDetails)
        return cities.map { it.cityName }
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
        val cityEntity = db.cityDao().getActiveCities().find { it.cityName == cityName }
        val currentEntity = db.currentWeatherDao().getForCity(cityName)
        val forecastEntities = db.forecastWeatherDao().getForCity(cityName)

        val current = currentEntity?.let {
            CurrentWeather(
                name = cityName,
                main = Main(
                    temp = it.temp,
                ),
                weather = listOf(
                    Weather(
                        icon = it.icon
                    )
                )
            )
        }

        val forecast = if (forecastEntities.isNotEmpty()) {
            ForecastWeather(
                list = forecastEntities.map { entity ->
                    ForecastItem(
                        dt = entity.date,
                        main = ForecastMain(
                            temp = entity.temp,
                            tempMin = entity.tempMin,
                            tempMax = entity.tempMax
                        ),
                        weather = listOf(
                            Weather(
                                icon = entity.icon
                            )
                        )
                    )
                }
            )
        } else null

        val timestampForecast = if (forecastEntities.isNotEmpty()) {
            forecastEntities.first().timestamp
        } else 0L

        if (cityEntity != null || current != null || forecast != null) {
            cachedDetails[cityName] = CachedWeatherData(
                current = current,
                forecast = forecast,
                timestampCurrent = currentEntity?.timestamp ?: 0L,
                timestampForecast = timestampForecast,
                orderIndex = cityEntity?.orderIndex ?: 0
            )
            _cachedWeatherLiveData.postValue(cachedDetails)
        }
    }

    suspend fun setCachedCurrent(cityName: String, current: CurrentWeather, timestamp: Long) {
        val existing = getCachedDetails(cityName) ?: CachedWeatherData()
        var orderIndex = existing.orderIndex
        if (orderIndex == 0) {
            orderIndex = (db.cityDao().getMaxOrderIndex() ?: 0) + 1
        }

        val newData = CachedWeatherData(
            current = current,
            forecast = existing.forecast,
            timestampCurrent = timestamp,
            timestampForecast = existing.timestampForecast,
            orderIndex = orderIndex
        )
        cachedDetails[cityName] = newData
        _cachedWeatherLiveData.postValue(cachedDetails)

        val cityEntity = CityEntity(
            cityName = cityName,
            orderIndex = orderIndex,
            lastUpdated = timestamp
        )
        db.cityDao().insert(cityEntity)

        val currentEntity = CurrentWeatherEntity(
            cityName = cityName,
            temp = current.main.temp,
            icon = current.weather.firstOrNull()?.icon ?: "",
            timestamp = timestamp
        )
        db.currentWeatherDao().insert(currentEntity)
    }

    suspend fun setCachedForecast(cityName: String, forecast: ForecastWeather, timestamp: Long) {
        val existing = getCachedDetails(cityName) ?: CachedWeatherData()
        var orderIndex = existing.orderIndex
        if (orderIndex == 0) {
            orderIndex = (db.cityDao().getMaxOrderIndex() ?: 0) + 1
        }

        val newData = CachedWeatherData(
            current = existing.current,
            forecast = forecast,
            timestampCurrent = existing.timestampCurrent,
            timestampForecast = timestamp,
            orderIndex = orderIndex
        )
        cachedDetails[cityName] = newData
        _cachedWeatherLiveData.postValue(cachedDetails)

        val cityEntity = CityEntity(
            cityName = cityName,
            orderIndex = orderIndex,
            lastUpdated = timestamp
        )
        db.cityDao().insert(cityEntity)

        db.forecastWeatherDao().deleteForCity(cityName)

        val forecastEntities = forecast.list.map { item ->
            ForecastWeatherEntity(
                cityName = cityName,
                date = item.dt,
                temp = item.main.temp,
                tempMax = item.main.tempMax,
                tempMin = item.main.tempMin,
                icon = item.weather.firstOrNull()?.icon ?: "",
                timestamp = timestamp
            )
        }
        db.forecastWeatherDao().insert(forecastEntities)
    }

    suspend fun removeCity(cityName: String) {
        cachedDetails.remove(cityName)
        _cachedWeatherLiveData.postValue(cachedDetails)

        db.cityDao().softDelete(cityName, System.currentTimeMillis())
    }
}