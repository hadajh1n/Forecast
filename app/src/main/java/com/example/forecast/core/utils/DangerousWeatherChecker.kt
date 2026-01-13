package com.example.forecast.core.utils

import android.content.Context
import com.example.forecast.R
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
        val forecast = try {
            RetrofitClient.weatherApi.getForecast(cityName)
        } catch (e: Exception) {
            return emptyList()
        }

        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis / MILLIS_IN_SECOND

        val tomorrowEnd = tomorrowStart + SECONDS_IN_DAY - 1

        val tomorrowItems = forecast.list.filter { it.dt in tomorrowStart..tomorrowEnd }

        if (tomorrowItems.isEmpty()) return emptyList()

        val minTemp = tomorrowItems.minOf { it.main.temp_min }
        val maxTemp = tomorrowItems.maxOf { it.main.temp_max }
        val maxWind = tomorrowItems.maxOf { it.wind?.speed ?: 0f }
        val totalRain = tomorrowItems.sumOf { (it.rain?.threeHours ?: 0f).toDouble() }.toFloat()
        val totalSnow = tomorrowItems.sumOf { (it.snow?.threeHours ?: 0f).toDouble() }.toFloat()

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