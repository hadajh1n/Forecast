package com.example.forecast

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.forecast.retrofit.RetrofitClient

class WeatherUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WeatherUpdateWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val apiKey = context.getString(R.string.weather_api_key)
            val cities = WeatherRepository.getCities()

            for (city in cities) {
                try {
                    val current = RetrofitClient.weatherApi.getCurrentWeather(city, apiKey)
                    WeatherRepository.setCachedCurrent(city, current, System.currentTimeMillis())

                    val forecast = RetrofitClient.weatherApi.getForecast(city, apiKey)
                    WeatherRepository.setCachedForecast(city, forecast, System.currentTimeMillis())
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