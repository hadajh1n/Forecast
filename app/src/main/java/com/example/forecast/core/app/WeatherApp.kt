package com.example.forecast.core.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.forecast.worker.DangerousWeatherWorker
import com.example.forecast.worker.WeatherUpdateWorker
import java.util.concurrent.TimeUnit

class WeatherApp : Application() {

    companion object {
        private const val BACKGROUND_UPDATE_INTERVAL_HOURS = 3L
        private const val NOTIFICATIONS_INTERVAL_HOURS = 8L
        lateinit var instance: WeatherApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this@WeatherApp

        setupBackgroundWeatherUpdates()
        setupDangerousWeatherWorker()
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

    private fun setupDangerousWeatherWorker() {
        val request = PeriodicWorkRequestBuilder<DangerousWeatherWorker>(
            NOTIFICATIONS_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "DangerousWeatherCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }
}