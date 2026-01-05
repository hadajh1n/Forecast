package com.example.forecast.network.retrofit

import com.example.forecast.data.dataclass.current.CurrentWeatherDTO
import com.example.forecast.data.dataclass.forecast.ForecastWeatherDTO
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ru"
    ): CurrentWeatherDTO

    @GET("forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ru"
    ): ForecastWeatherDTO
}