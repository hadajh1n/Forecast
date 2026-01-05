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

    companion object {
        private const val TAG = "WeatherUpdateWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val cities = WeatherRepository.getMemoryCities()

            for (city in cities) {
                try {
                    val current = RetrofitClient.weatherApi.getCurrentWeather(city)
                    WeatherRepository.setCachedCurrent(city, current)

                    val forecast = RetrofitClient.weatherApi.getForecast(city)
                    WeatherRepository.setCachedForecast(city, forecast)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка обновления для $city", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка выполнения фонового обновления", e)
            Result.retry()
        }
    }
}