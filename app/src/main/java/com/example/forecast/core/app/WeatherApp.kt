package com.example.forecast.core.app

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.forecast.core.utils.CacheConfig
import com.example.forecast.worker.DangerousWeatherWorker
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

        Log.e("WeatherUpdateWorker", "Запуск фонового обновления")
        setupBackgroundWeatherUpdates()
        setupDangerousWeatherWorker()
    }

    private fun setupBackgroundWeatherUpdates() {
        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            CacheConfig.BACKGROUND_UPDATE_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this@WeatherApp).enqueueUniquePeriodicWork(
            "WeatherAutoUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun setupDangerousWeatherWorker() {
        val workRequest = PeriodicWorkRequestBuilder<DangerousWeatherWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DangerousWeatherCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}