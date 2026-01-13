package com.example.forecast.data.repository

import androidx.room.Room
import com.example.forecast.core.app.WeatherApp
import com.example.forecast.data.dataclass.current.CurrentWeatherCache
import com.example.forecast.data.dataclass.current.CurrentWeatherDTO
import com.example.forecast.data.dataclass.forecast.ForecastWeatherCache
import com.example.forecast.data.dataclass.forecast.ForecastWeatherDTO
import com.example.forecast.data.mapper.current.CurrentWeatherCacheEntityMapper
import com.example.forecast.data.mapper.current.CurrentWeatherDtoCacheMapper
import com.example.forecast.data.mapper.current.CurrentWeatherEntityCacheMapper
import com.example.forecast.data.mapper.forecast.ForecastWeatherCacheEntityMapper
import com.example.forecast.data.mapper.forecast.ForecastWeatherDtoCacheMapper
import com.example.forecast.data.mapper.forecast.ForecastWeatherEntityCacheMapper
import com.example.forecast.data.room.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WeatherRepository {

    data class CachedWeatherData(
        val current: CurrentWeatherCache? = null,
        val forecast: ForecastWeatherCache? = null
    )

    private val memoryCache = mutableMapOf<String, CachedWeatherData>()
    private val cacheMutex = Mutex()

    private val currentDtoMapper = CurrentWeatherDtoCacheMapper()
    private val currentCacheEntityMapper = CurrentWeatherCacheEntityMapper()
    private val currentEntityCacheMapper = CurrentWeatherEntityCacheMapper()

    private val forecastDtoMapper = ForecastWeatherDtoCacheMapper()
    private val forecastCacheEntityMapper = ForecastWeatherCacheEntityMapper()
    private val forecastEntityCacheMapper = ForecastWeatherEntityCacheMapper()

    private val db by lazy {
        Room.databaseBuilder(
            WeatherApp.instance.applicationContext,
            AppDatabase::class.java,
            "weather-db"
        ).build()
    }

    private suspend fun loadCacheFromDb() {
        val currentEntities = db.currentWeatherDao().getAllCities()

        for (currentEntity in currentEntities) {
            val forecastEntities =
                db.forecastWeatherDao().getForCityForecast(currentEntity.cityName)

            memoryCache[currentEntity.cityName] = CachedWeatherData(
                current = currentEntityCacheMapper.fromEntityToCache(currentEntity),
                forecast = forecastEntityCacheMapper.fromEntityToCache(
                    cityName = currentEntity.cityName,
                    entities = forecastEntities
                )
            )
        }
    }

    suspend fun initCacheFromDb() {
        loadCacheFromDb()
    }

    suspend fun getMemoryCities(): List<String> = cacheMutex.withLock {
        memoryCache.keys.toList()
    }

    suspend fun getCachedDetails(cityName: String): CachedWeatherData? = cacheMutex.withLock {
        memoryCache[cityName]
    }

    suspend fun setCachedCurrent(
        cityName: String,
        dto: CurrentWeatherDTO
    ) = cacheMutex.withLock {

        val cache = currentDtoMapper.fromDtoToCache(dto)

        val existingEntity = db.currentWeatherDao().getForCityCurrent(cityName)
        val orderIndex = existingEntity?.orderIndex
            ?: (db.currentWeatherDao().getMaxIndex() ?: 0) + 1

        val entity = currentCacheEntityMapper.fromCacheToEntity(
            cache = cache,
            orderIndex = orderIndex
        )

        db.currentWeatherDao().insertCurrent(entity)

        memoryCache[cityName] =
            (memoryCache[cityName] ?: CachedWeatherData()).copy(current = cache)
    }

    suspend fun setCachedForecast(
        cityName: String,
        dto: ForecastWeatherDTO
    ) = cacheMutex.withLock {

        val cache = forecastDtoMapper.fromDtoToCache(cityName, dto)

        val entities = cache.items.map {
            forecastCacheEntityMapper.fromCacheToEntity(
                cityName = cityName,
                item = it,
                timestamp = cache.timestamp
            )
        }

        db.forecastWeatherDao().deleteForCityForecast(cityName)
        db.forecastWeatherDao().insert(entities)

        memoryCache[cityName] =
            (memoryCache[cityName] ?: CachedWeatherData()).copy(forecast = cache)
    }

    suspend fun removeCity(cityName: String) = cacheMutex.withLock {
        memoryCache.remove(cityName)
        db.currentWeatherDao().deleteForCityCurrent(cityName)
        db.forecastWeatherDao().deleteForCityForecast(cityName)
    }
}