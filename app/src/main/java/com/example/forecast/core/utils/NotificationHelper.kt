package com.example.forecast.core.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.os.Bundle
import androidx.navigation.NavDeepLinkBuilder
import com.example.forecast.R

class NotificationHelper(context: Context) {

    companion object {
        private const val CHANNEL_ID = "dangerous_weather_channel"
        private const val CHANNEL_NAME = "Опасная погода"
        private const val CHANNEL_DESCRIPTION = "Уведомления об опасных погодных условиях"
    }

    private val appContext = context.applicationContext

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(
        title: String,
        message: String,
        cityName: String
    ) {
        val notificationId = cityName.hashCode()
        val intent = NavDeepLinkBuilder(appContext)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.detailFragment)
            .setArguments(Bundle().apply { putString("cityName", cityName) })
            .createPendingIntent()

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(appContext)) {
            notify(notificationId, builder.build())
        }
    }
}