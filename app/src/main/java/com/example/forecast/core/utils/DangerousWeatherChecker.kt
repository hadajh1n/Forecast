package com.example.forecast.core.utils

import android.content.Context
import com.example.forecast.R
import com.example.forecast.data.dataclass.forecast.ForecastItemCache
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.network.retrofit.RetrofitClient
import java.util.Calendar

object DangerousWeatherChecker {

    private const val SECONDS_IN_DAY = 24 * 60 * 60
    private const val MILLIS_IN_SECOND = 1000
    private const val VERY_LOW_TEMP = -30f
    private const val VERY_HIGH_TEMP = 30f
    private const val STRONG_SNOW_MM = 10f
    private const val STRONG_RAIN_MM = 20f
    private const val STRONG_WIND_MS = 15f

    suspend fun getTomorrowDangerWarnings(context: Context, cityName: String): List<String> {

        val forecastCache = WeatherRepository.getCachedDetails(cityName)?.forecast

        val forecastItemsTomorrow = try {
            val dtoForecast = RetrofitClient.weatherApi.getForecast(cityName)
            WeatherRepository.setCachedForecast(cityName, dtoForecast)

            val forecastItems = dtoForecast.list.map {
                ForecastItemCache(
                    dt = it.dt,
                    tempMax = it.main.temp_max,
                    tempMin = it.main.temp_min,
                    icon = it.weather.firstOrNull()?.icon.orEmpty(),
                    wind = it.wind?.speed,
                    rain = it.rain?.threeHours,
                    snow = it.snow?.threeHours
                )
            }

            val tomorrowStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis / MILLIS_IN_SECOND

            val tomorrowEnd = tomorrowStart + SECONDS_IN_DAY - 1

            forecastItems.filter { it.dt in tomorrowStart..tomorrowEnd }
        } catch (e: Exception) {
            forecastCache?.items?.filter {
                val dt = it.dt
                val tomorrowStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis / MILLIS_IN_SECOND
                val tomorrowEnd = tomorrowStart + SECONDS_IN_DAY - 1
                dt in tomorrowStart..tomorrowEnd
            } ?: emptyList()
        }

        if (forecastItemsTomorrow.isEmpty()) return emptyList()

        val minTemp = forecastItemsTomorrow.minOf { it.tempMin }
        val maxTemp = forecastItemsTomorrow.maxOf { it.tempMax }
        val maxWind = forecastItemsTomorrow.maxOf { it.wind ?: 0f }
        val totalRain = forecastItemsTomorrow.sumOf { (it.rain ?: 0f).toDouble() }.toFloat()
        val totalSnow = forecastItemsTomorrow.sumOf { (it.snow ?: 0f).toDouble() }.toFloat()

        val warnings = mutableListOf<String>()

        if (minTemp <= VERY_LOW_TEMP) {
            warnings += context.getString(R.string.very_low_temperature_warning, minTemp.toInt())
        }
        if (maxTemp >= VERY_HIGH_TEMP) {
            warnings += context.getString(R.string.very_high_temperature_warning, maxTemp.toInt())
        }
        if (totalSnow >= STRONG_SNOW_MM) {
            warnings += context.getString(R.string.strong_snow_warning, totalSnow.toInt())
        }
        if (totalRain >= STRONG_RAIN_MM) {
            warnings += context.getString(R.string.strong_rain_warning, totalRain.toInt())
        }
        if (maxWind >= STRONG_WIND_MS) {
            warnings += context.getString(R.string.strong_wind_warning, maxWind.toInt())
        }

        return warnings
    }
}