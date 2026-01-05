package com.example.forecast.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.forecast.R
import com.example.forecast.core.utils.NotificationHelper
import com.example.forecast.core.utils.PreferencesHelper
import com.example.forecast.data.dataclass.forecast.ForecastWeatherCache
import com.example.forecast.data.repository.WeatherRepository
import java.util.Calendar

class DangerousWeatherWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val cities = WeatherRepository.getMemoryCities()
        val notificationHelper = NotificationHelper(applicationContext)

        for (city in cities) {

            if (!PreferencesHelper.isDangerousWeatherEnabled(applicationContext, city)) continue

            WeatherRepository.refreshForecast(city)

            val forecastCache =
                WeatherRepository.getForecastCache(city) ?: continue

            val dangerousEvents =
                getDangerousTemperatureTomorrow(forecastCache)

            if (dangerousEvents.isNotEmpty()) {
                val title =
                    applicationContext.getString(R.string.dangerous_weather_title)
                val message =
                    applicationContext.getString(
                        R.string.dangerous_weather_message,
                        city,
                        dangerousEvents.joinToString(", ")
                    )

                notificationHelper.sendNotification(title, message, city)
            }
        }

        return Result.success()
    }

    private fun getDangerousTemperatureTomorrow(
        forecast: ForecastWeatherCache
    ): List<String> {

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowDay = calendar.get(Calendar.DAY_OF_YEAR)

        val tomorrowItems = forecast.items.filter { item ->
            Calendar.getInstance().apply {
                timeInMillis = item.dt * 1000
            }.get(Calendar.DAY_OF_YEAR) == tomorrowDay
        }

        if (tomorrowItems.isEmpty()) return emptyList()

        val minTemp = tomorrowItems.minOf { it.tempMin }.toInt()
        val maxTemp = tomorrowItems.maxOf { it.tempMax }.toInt()

        val result = mutableListOf<String>()

        if (minTemp <= -30) {
            result += applicationContext.getString(
                R.string.low_temperature_warning, minTemp
            )
        }

        if (maxTemp >= 30) {
            result += applicationContext.getString(
                R.string.heat_warning, maxTemp
            )
        }

        return result
    }
}