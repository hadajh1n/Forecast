package com.example.forecast.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.network.retrofit.RetrofitClient

class WeatherUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cities = WeatherRepository.getMemoryCities()

            for (city in cities) {
                try {
                    val current = RetrofitClient.weatherApi.getCurrentWeather(city)
                    WeatherRepository.setCachedCurrent(city, current)

                    val forecast = RetrofitClient.weatherApi.getForecast(city)
                    WeatherRepository.setCachedForecast(city, forecast)
                    Log.e("WeatherUpdateWorker", "Запрос API для города: $city")
                } catch (e: Exception) {
                    Log.e("WeatherUpdateWorker", "Ошибка обновления для города: $city")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherUpdateWorker", "Ошибка выполнения фонового обновления")
            Result.retry()
        }
    }
}