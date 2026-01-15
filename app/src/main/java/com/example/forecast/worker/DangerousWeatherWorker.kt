package com.example.forecast.worker

import android.content.Context
import androidx.work.CoroutineWorker
import com.example.forecast.R
import androidx.work.WorkerParameters
import com.example.forecast.core.utils.DangerousWeatherChecker
import com.example.forecast.core.utils.NotificationHelper
import com.example.forecast.core.utils.PreferencesHelper
import com.example.forecast.data.repository.WeatherRepository

class DangerousWeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val PREF_NAME = "weather_notifications"
        private const val LAST_NOTIFICATION_PREFIX = "last_notification"
    }

    override suspend fun doWork(): Result {
        return try {
            WeatherRepository.initCacheFromDb()
            val cities = WeatherRepository.getMemoryCities()

            cities.forEach { city ->
                if (PreferencesHelper.isDangerousWeatherEnabled(applicationContext, city)) {
                    val warnings = DangerousWeatherChecker.getTomorrowDangerWarnings(
                        applicationContext,
                        city
                    )

                    if (warnings.isNotEmpty()) {
                        showNotificationIfNeeded(applicationContext, city, warnings)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotificationIfNeeded(
        context: Context,
        city: String,
        warnings: List<String>
    ) {
        val message = warnings.joinToString("\n")
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = LAST_NOTIFICATION_PREFIX + city
        val lastMessage = prefs.getString(key, null)

        if (message != lastMessage) {
            NotificationHelper(context).sendNotification(
                title = context.getString(R.string.dangerous_weather_tomorrow_title, city),
                message = message,
                cityName = city
            )

            prefs.edit().putString(key, message).apply()
        }
    }
}