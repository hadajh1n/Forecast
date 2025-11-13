package com.example.forecast.worker

import android.content.Context
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.forecast.R
import com.example.forecast.core.utils.NotificationHelper
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.network.retrofit.RetrofitClient

class DangerousWeatherWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DangerousWeatherWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val cities = WeatherRepository.getCities()

            for (city in cities) {
                try {
                    val forecast = RetrofitClient.weatherApi.getForecast(city)
                    WeatherRepository.setCachedForecast(city, forecast, System.currentTimeMillis())

                    val dangerousEvents = getDangerousWeatherTomorrow(forecast)
                    if (dangerousEvents.isNotEmpty()) {
                        val notificationHelper = NotificationHelper(context)
                        val title = context.getString(R.string.dangerous_weather_title)
                        val message = context.getString(
                            R.string.dangerous_weather_message,
                            city,
                            dangerousEvents.joinToString(", ")
                        )
                        notificationHelper.sendNotification(title, message, city)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при проверке опасной погоды для $city", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка выполнения DangerousWeatherWorker", e)
            Result.retry()
        }
    }

    private fun getDangerousWeatherTomorrow(
        forecast: com.example.forecast.data.dataclass.ForecastWeather
    ): List<String> {
        val dangerousEvents = mutableListOf<String>()
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val tomorrowDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)

        val tomorrowForecast = forecast.list.filter { item ->
            val itemCal = java.util.Calendar.getInstance()
            itemCal.timeInMillis = item.dt * 1000
            itemCal.get(java.util.Calendar.DAY_OF_YEAR) == tomorrowDay
        }

        for (item in tomorrowForecast) {
            val temp = item.main.temp
            val windSpeed = item.main.tempMax
            val description = item.weather.firstOrNull()?.description?.lowercase() ?: ""

            if (temp <= -20)
                dangerousEvents += context.getString(
                    R.string.low_temperature_warning, temp.toInt()
                )

            if (temp >= 35)
                dangerousEvents += context.getString(R.string.heat_warning, temp.toInt())

            if (windSpeed >= 15)
                dangerousEvents += context.getString(
                    R.string.strong_wind_warning, windSpeed.toInt()
                )

            if (listOf("гроза", "ураган", "ливень", "снегопад").any { it in description }) {
                dangerousEvents += context.getString(R.string.natural_phenomenon, description)
            }
        }

        return dangerousEvents
    }
}