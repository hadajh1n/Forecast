package com.example.forecast.core.app

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.forecast.alarm.DangerousWeatherAlarmReceiver
import com.example.forecast.worker.WeatherUpdateWorker
import java.util.concurrent.TimeUnit

class WeatherApp : Application() {

    companion object {
        private const val BACKGROUND_UPDATE_INTERVAL_HOURS = 3L
        private const val ALARM_REQUEST_CODE = 1001
        lateinit var instance: WeatherApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this@WeatherApp

        setupBackgroundWeatherUpdates()
        setupDangerousWeatherAlarm()
    }

    private fun setupBackgroundWeatherUpdates() {
        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            BACKGROUND_UPDATE_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this@WeatherApp).enqueueUniquePeriodicWork(
            "WeatherAutoUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun setupDangerousWeatherAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DangerousWeatherAlarmReceiver::class.java)

        val existingPendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (existingPendingIntent != null) return

        val triggerTime = System.currentTimeMillis()
        val newPendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                newPendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                newPendingIntent
            )
        }
    }
}