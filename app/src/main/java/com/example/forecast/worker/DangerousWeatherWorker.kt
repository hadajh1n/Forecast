package com.example.forecast.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.forecast.R
import com.example.forecast.core.utils.NotificationHelper
import com.example.forecast.core.utils.PreferencesHelper
import com.example.forecast.data.dataclass.ForecastWeather
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.network.retrofit.RetrofitClient
import java.util.Calendar

class DangerousWeatherWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private companion object {
        const val TAG = "DangerousWeatherWorker"
    }

    override suspend fun doWork(): Result {
        val cities = WeatherRepository.getMemoryCities()
        val notificationHelper = NotificationHelper(applicationContext)

        for (city in cities) {

            if (!PreferencesHelper.isDangerousWeatherEnabled(applicationContext, city)) continue

            val forecast = RetrofitClient.weatherApi.getForecast(city)
            WeatherRepository.setCachedForecast(city, forecast, System.currentTimeMillis())

            val dangerousEvents = getDangerousWeatherTomorrow(forecast)

            if (dangerousEvents.isNotEmpty()) {

                Log.d(
                    TAG,
                    "Уведомление для $city: ${dangerousEvents.joinToString()}"
                )

                val title = applicationContext.getString(R.string.dangerous_weather_title)
                val message = applicationContext.getString(
                    R.string.dangerous_weather_message,
                    city,
                    dangerousEvents.joinToString(", ")
                )

                notificationHelper.sendNotification(title, message, city)
            }
        }

        return Result.success()
    }

    private fun getDangerousWeatherTomorrow(
        forecast: ForecastWeather
    ): List<String> {

        var minTemp: Int? = null
        var maxTemp: Int? = null
        var maxWind: Int? = null
        val phenomena = mutableSetOf<String>()

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowDay = calendar.get(Calendar.DAY_OF_YEAR)

        val tomorrowForecast = forecast.list.filter { item ->
            Calendar.getInstance().apply {
                timeInMillis = item.dt * 1000
            }.get(Calendar.DAY_OF_YEAR) == tomorrowDay
        }

        for (item in tomorrowForecast) {
            val temp = item.main.temp.toInt()
            val wind = item.main.tempMax.toInt()
            val description = item.weather.firstOrNull()?.description?.lowercase() ?: ""

            minTemp = minTemp?.coerceAtMost(temp) ?: temp
            maxTemp = maxTemp?.coerceAtLeast(temp) ?: temp
            maxWind = maxWind?.coerceAtLeast(wind) ?: wind

            listOf("гроза", "ураган", "ливень", "снегопад")
                .filter { it in description }
                .forEach { phenomena.add(it) }
        }

        val result = mutableListOf<String>()

        minTemp?.takeIf { it <= -20 }?.let {
            result += applicationContext.getString(
                R.string.low_temperature_warning, it
            )
        }

        maxTemp?.takeIf { it >= 35 }?.let {
            result += applicationContext.getString(
                R.string.heat_warning, it
            )
        }

        maxWind?.takeIf { it >= 15 }?.let {
            result += applicationContext.getString(
                R.string.strong_wind_warning, it
            )
        }

        phenomena.forEach {
            result += applicationContext.getString(
                R.string.natural_phenomenon, it
            )
        }

        return result
    }
}