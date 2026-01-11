package com.example.forecast.core.utils

import android.util.Log
import com.example.forecast.network.retrofit.RetrofitClient
import java.util.Calendar

object DangerousWeatherChecker {

    suspend fun getTomorrowDangerWarnings(cityName: String): List<String> {
        val forecast = try {
            RetrofitClient.weatherApi.getForecast(cityName)
        } catch (e: Exception) {
            Log.e("DangerousWeather", "Ошибка загрузки прогноза", e)
            return emptyList()
        }

        Log.d("DangerousWeather", "Прогноз успешно загружен, элементов: ${forecast.list.size}")

        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000

        val tomorrowEnd = tomorrowStart + 86399

        val tomorrowItems = forecast.list.filter { it.dt in tomorrowStart..tomorrowEnd }

        if (tomorrowItems.isEmpty()) return emptyList()

        val minTemp = tomorrowItems.minOf { it.main.temp_min }
        val maxTemp = tomorrowItems.maxOf { it.main.temp_max }
        val maxWind = tomorrowItems.maxOf { it.wind?.speed ?: 0f }
        val totalRain = tomorrowItems.sumOf { (it.rain?.`3h` ?: 0f).toDouble() }.toFloat()
        val totalSnow = tomorrowItems.sumOf { (it.snow?.`3h` ?: 0f).toDouble() }.toFloat()

        val warnings = mutableListOf<String>()

        if (minTemp <= -30f)   warnings.add("Очень низкая температура: ${minTemp.toInt()}°C")
        if (maxTemp >= 30f)    warnings.add("Очень высокая температура: ${maxTemp.toInt()}°C")
        if (totalSnow >= 10f)  warnings.add("Сильный снегопад: ${totalSnow.toInt()} мм")
        if (totalRain >= 20f)  warnings.add("Сильный дождь: ${totalRain.toInt()} мм")
        if (maxWind >= 15f)    warnings.add("Сильный ветер: ${maxWind.toInt()} м/с")

        Log.d(
            "DangerousWeather",
            "Результаты анализа: " +
                    "мин. температура=$minTemp, " +
                    "макс. температура=$maxTemp, " +
                    "ветер=$maxWind, " +
                    "дождь=$totalRain, " +
                    "снег=$totalSnow"
        )

        Log.d("DangerousWeather", "Найдено предупреждений: ${warnings.size}")
        return warnings
    }
}