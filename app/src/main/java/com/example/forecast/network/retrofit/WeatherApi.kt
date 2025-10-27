package com.example.forecast.network.retrofit

import com.example.forecast.data.dataclass.CurrentWeather
import com.example.forecast.data.dataclass.ForecastWeather
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ru"
    ): CurrentWeather

    @GET("forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ru"
    ) : ForecastWeather
}