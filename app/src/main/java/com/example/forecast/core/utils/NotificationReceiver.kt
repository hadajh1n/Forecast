package com.example.forecast.core.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DangerousWeather", "Получен broadcast для показа уведомления")

        val title = intent.getStringExtra("title") ?: return
        val message = intent.getStringExtra("message") ?: return
        val cityName = intent.getStringExtra("cityName") ?: return

        if (title == null || message == null || cityName == null) {
            Log.e("DangerousWeather", "Ошибка: отсутствуют данные в intent")
            return
        }

        Log.d("DangerousWeather", "Показ уведомления для города: $cityName")

        val notificationHelper = NotificationHelper(context)
        notificationHelper.sendNotification(title, message, cityName)
    }
}

fun scheduleImmediateNotification(
    context: Context,
    title: String,
    message: String,
    cityName: String
) {
    Log.d("DangerousWeather", "Планирую уведомление для города: $cityName")

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, NotificationReceiver::class.java).apply {
        putExtra("title", title)
        putExtra("message", message)
        putExtra("cityName", cityName)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        cityName.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    alarmManager.setExact(
        AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis(),
        pendingIntent
    )

    Log.d("DangerousWeather", "AlarmManager.setExact успешно вызван")
}