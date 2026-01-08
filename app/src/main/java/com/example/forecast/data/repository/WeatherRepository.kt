package com.example.forecast.data.repository

import android.util.Log
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
import com.example.forecast.network.retrofit.RetrofitClient
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
        Log.e("MainViewModel", "loadCacheFromDb: найдено городов в БД = ${currentEntities.size}")

        for (currentEntity in currentEntities) {
            val forecastEntities =
                db.forecastWeatherDao().getForCityForecast(currentEntity.cityName)

            Log.e(
                "MainViewModel",
                "loadCacheFromDb: загрузка города ${currentEntity.cityName}, forecast = ${forecastEntities.size}"
            )

            memoryCache[currentEntity.cityName] = CachedWeatherData(
                current = currentEntityCacheMapper.fromEntityToCache(currentEntity),
                forecast = forecastEntityCacheMapper.fromEntityToCache(
                    cityName = currentEntity.cityName,
                    entities = forecastEntities
                )
            )
        }

        Log.e("MainViewModel", "loadCacheFromDb: загрузка завершена")
    }

    suspend fun initCacheFromDb() {
        Log.e("MainViewModel", "loadCacheFromDb: попытка загрузки кэша из БД")
        loadCacheFromDb()
    }

    suspend fun getMemoryCities(): List<String> = cacheMutex.withLock {
        Log.e("MainViewModel", "getMemoryCities: запрос списка городов")
        val cities = memoryCache.keys.toList()
        Log.e("MainViewModel", "getMemoryCities: найдено городов = ${cities.size}")

        cities
    }

    suspend fun getCachedDetails(cityName: String): CachedWeatherData? =
        cacheMutex.withLock {
            val cached = memoryCache[cityName]

            Log.e(
                "MainViewModel",
                "getCachedDetails: city=$cityName, " +
                        "current=${cached?.current != null}, " +
                        "forecast=${cached?.forecast != null}"
            )

            cached
        }

    suspend fun setCachedCurrent(
        cityName: String,
        dto: CurrentWeatherDTO
    ) = cacheMutex.withLock {

        Log.e("MainViewModel", "setCachedCurrent: city=$cityName")

        val cache = currentDtoMapper.fromDtoToCache(dto)

        val existingEntity = db.currentWeatherDao().getForCityCurrent(cityName)
        val orderIndex = existingEntity?.orderIndex
            ?: (db.currentWeatherDao().getMaxIndex() ?: 0) + 1

        Log.e(
            "MainViewModel",
            "setCachedCurrent: orderIndex=$orderIndex, existed=${existingEntity != null}"
        )

        val entity = currentCacheEntityMapper.fromCacheToEntity(
            cache = cache,
            orderIndex = orderIndex
        )

        db.currentWeatherDao().insertCurrent(entity)

        memoryCache[cityName] =
            (memoryCache[cityName] ?: CachedWeatherData()).copy(current = cache)

        Log.e("MainViewModel", "setCachedCurrent: сохранено в memoryCache и БД")
    }

    suspend fun setCachedForecast(
        cityName: String,
        dto: ForecastWeatherDTO
    ) = cacheMutex.withLock {

        Log.e("MainViewModel", "setCachedForecast: city=$cityName")

        val cache = forecastDtoMapper.fromDtoToCache(cityName, dto)

        val entities = cache.items.map {
            forecastCacheEntityMapper.fromCacheToEntity(
                cityName = cityName,
                item = it,
                timestamp = cache.timestamp
            )
        }

        Log.e(
            "MainViewModel",
            "setCachedForecast: элементов прогноза = ${entities.size}"
        )

        db.forecastWeatherDao().deleteForCityForecast(cityName)
        db.forecastWeatherDao().insert(entities)

        memoryCache[cityName] =
            (memoryCache[cityName] ?: CachedWeatherData()).copy(forecast = cache)

        Log.e("MainViewModel", "setCachedForecast: прогноз обновлён")
    }

    suspend fun removeCity(cityName: String) = cacheMutex.withLock {
        Log.e("MainViewModel", "removeCity: удаление города $cityName")

        memoryCache.remove(cityName)
        db.currentWeatherDao().deleteForCityCurrent(cityName)
        db.forecastWeatherDao().deleteForCityForecast(cityName)
    }

    suspend fun refreshForecast(cityName: String) {
        Log.e("MainViewModel", "refreshForecast: запрос API для $cityName")

        val dto = RetrofitClient.weatherApi.getForecast(cityName)
        setCachedForecast(cityName, dto)

        Log.e("MainViewModel", "refreshForecast: обновление завершено")
    }

    suspend fun getForecastCache(cityName: String): ForecastWeatherCache? =
        cacheMutex.withLock {
            val forecast = memoryCache[cityName]?.forecast

            Log.e(
                "MainViewModel",
                "getForecastCache: city=$cityName, exists=${forecast != null}"
            )

            forecast
        }
}