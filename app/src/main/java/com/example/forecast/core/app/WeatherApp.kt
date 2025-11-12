package com.example.forecast.core.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.forecast.core.utils.Constants
import com.example.forecast.worker.WeatherUpdateWorker
import java.util.concurrent.TimeUnit

class WeatherApp : Application() {
    companion object {
        lateinit var instance: WeatherApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this@WeatherApp

        setupBackgroundWeatherUpdates()
    }

    private fun setupBackgroundWeatherUpdates() {
        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            Constants.CacheLifetime.BACKGROUND_UPDATE_INTERVAL, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this@WeatherApp).enqueueUniquePeriodicWork(
            "WeatherAutoUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}