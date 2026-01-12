package com.example.forecast.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.forecast.core.utils.NotificationHelper

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val message = intent.getStringExtra("message") ?: return
        val cityName = intent.getStringExtra("cityName") ?: return

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
}