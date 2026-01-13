package com.example.forecast.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import com.example.forecast.R
import androidx.work.WorkerParameters
import com.example.forecast.core.utils.DangerousWeatherChecker
import com.example.forecast.core.utils.NotificationHelper
import com.example.forecast.core.utils.PreferencesHelper
import com.example.forecast.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DangerousWeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DangerousWeatherWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "| НАЧАЛО ПРОВЕРКИ ОПАСНОЙ ПОГОДЫ |")
        try {
            WeatherRepository.initCacheFromDb()
            val cities = WeatherRepository.getMemoryCities()
            Log.d(TAG, "Найдено городов в кэше: ${cities.joinToString()}")

            cities.forEach { city ->
                Log.d(TAG, "\nПроверка города: $city")
                if (PreferencesHelper.isDangerousWeatherEnabled(applicationContext, city)) {
                    Log.d(TAG, "Подписка ВКЛ для $city")
                    val warnings = DangerousWeatherChecker.getTomorrowDangerWarnings(
                        applicationContext,
                        city
                    )

                    if (warnings.isNotEmpty()) {
                        Log.d(TAG, "| ОБНАРУЖЕНА ОПАСНАЯ ПОГОДА для $city |")
                        warnings.forEach { Log.d(TAG, "   • $it") }
                        showNotificationIfNeeded(applicationContext, city, warnings)
                    } else {
                        Log.d(TAG, "Опасных условий нет для города $city")
                    }
                } else {
                    Log.d(TAG, "Подписка ВЫКЛ для $city — пропускаю")
                }
            }
            Log.d(TAG, "| ПРОВЕРКА ЗАВЕРШЕНА УСПЕШНО |\n")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в Worker: ${e.message}", e)
            Result.retry()
        }
    }

    private fun showNotificationIfNeeded(
        context: Context,
        city: String,
        warnings: List<String>
    ) {
        val message = warnings.joinToString("\n")

        val prefs = context.getSharedPreferences("weather_notifications", Context.MODE_PRIVATE)
        val key = "last_notification_$city"
        val lastMessage = prefs.getString(key, null)

        if (message != lastMessage) {
            Log.d(TAG, "ПОКАЗЫВАЮ УВЕДОМЛЕНИЕ для города $city")
            Log.d(TAG, "Заголовок: Опасная погода завтра в городе $city")
            Log.d(TAG, "Текст: $message")

            NotificationHelper(context).sendNotification(
                title = context.getString(R.string.dangerous_weather_tomorrow_title, city),
                message = message,
                cityName = city
            )

            prefs.edit().putString(key, message).apply()
        } else {
            Log.d(TAG, "Уведомление не показываю — уже было отправлено для города $city")
        }
    }
}