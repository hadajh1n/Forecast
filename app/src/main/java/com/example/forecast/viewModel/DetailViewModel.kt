package com.example.forecast.viewModel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.Constants
import com.example.forecast.R
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastItem
import com.example.forecast.dataclass.ForecastMain
import com.example.forecast.dataclass.ForecastUI
import com.example.forecast.dataclass.ForecastWeather
import com.example.forecast.dataclass.Weather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class DetailUIState {
    object Loading : DetailUIState()

    data class Success(
        val temperature: String,
        val iconUrl: String,
        val forecast: List<ForecastUI>
    ) : DetailUIState()

    data class Error(val message: String) : DetailUIState()
}

data class CachedWeatherData(
    val weather: CurrentWeather,
    val forecast: ForecastWeather,
    val timestamp: Long
)

class DetailViewModel : ViewModel() {

    companion object {
        private const val SECONDS_TO_MILLIS = 1000L
        private const val WEATHER_ICON_URL = "https://openweathermap.org/img/wn/%s.png"
    }

    private val _detailState = MutableLiveData<DetailUIState>()

    val detailState: LiveData<DetailUIState> get() = _detailState

    private var cachedDetails = mutableMapOf<String, CachedWeatherData>()
    private var refreshJob: Job? = null

    private fun getApiKey(context: Context) : String {
        val apiKey = context.getString(R.string.weather_api_key)
        if (apiKey.isEmpty()) {
            throw IllegalStateException("Missing API key")
        }

        return apiKey
    }

    fun loadCityDetail(cityName: String, context: Context) {
        viewModelScope.launch {
            val cachedData = cachedDetails[cityName]
            if (cachedData != null && isCachedValid(cachedData.timestamp)) {
                _detailState.value = mapToUI(cachedData, context)
                return@launch
            }

            fetchWeatherData(cityName, context, showLoading = cachedData == null)
        }
    }

    private fun isCachedValid(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    private suspend fun fetchWeatherData(
        cityName: String,
        context: Context,
        showLoading: Boolean = false
    ) {
        if (showLoading) {
            _detailState.value = DetailUIState.Loading
        }
        try {
            val apiKey = getApiKey(context)
            val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
            val forecastResponse = RetrofitClient.weatherApi.getForecast(cityName, apiKey)

            cachedDetails[cityName] = CachedWeatherData(
                weather = weather,
                forecast = forecastResponse,
                timestamp = System.currentTimeMillis()
            )

            _detailState.value = mapToUI(cachedDetails[cityName]!!, context)
        } catch (e: Exception) {
            _detailState.value = DetailUIState.Error(
                context.getString(R.string.error_fetch_current_weather),
            )
        }
    }

    private fun mapToUI(cached: CachedWeatherData, context: Context): DetailUIState.Success {
        val dailyForecasts = groupForecastByDay(cached.forecast)
        val uiForecast = dailyForecasts
            .take(6)
            .map {
                ForecastUI(
                    dayOfWeek = SimpleDateFormat("E", Locale("ru"))
                        .format(Date(it.dt * SECONDS_TO_MILLIS))
                        .uppercase(),
                    iconUrl = WEATHER_ICON_URL.format(it.weather[0].icon),
                    tempMax = context.getString(
                        R.string.temperature_format,
                        it.main.tempMax.toInt()
                    ),
                    tempMin = context.getString(
                        R.string.temperature_format,
                        it.main.tempMin.toInt()
                    )
                )
            }

        return DetailUIState.Success(
            temperature = context.getString(
                R.string.temperature_format,
                cached.weather.main.temp.toInt()
            ),
            iconUrl = WEATHER_ICON_URL.format(cached.weather.weather[0].icon),
            forecast = uiForecast
        )
    }

    private fun groupForecastByDay(response: ForecastWeather): List<ForecastItem> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val nextDay = calendar.get(Calendar.DAY_OF_YEAR)
        val dailyForecasts = mutableListOf<ForecastItem>()
        val groupedByDay = response.list.groupBy { item ->
            val itemCal = Calendar.getInstance()
            itemCal.time = Date(item.dt * SECONDS_TO_MILLIS)
            itemCal.get(Calendar.DAY_OF_YEAR)
        }

        for (day in nextDay until nextDay + 6) {
            val dayData = groupedByDay[day]
            if (dayData != null && dayData.isNotEmpty()) {
                val maxTemp = dayData.maxOfOrNull { it.main.temp } ?: 0f
                val minTemp = dayData.minOfOrNull { it.main.temp } ?: 0f
                val icon = dayData.first().weather[0].icon
                val dt = dayData.first().dt
                dailyForecasts.add(
                    ForecastItem(
                        dt = dt,
                        main = ForecastMain(temp = 0f, tempMax = maxTemp, tempMin = minTemp),
                        weather = listOf(Weather(icon = icon))
                    )
                )
            }
        }

        return dailyForecasts
    }

    fun startRefresh(
        cityName: String,
        context: Context
    ) {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            while (isActive) {
                val cachedData = cachedDetails[cityName]
                val age = cachedData?.let {
                    System.currentTimeMillis() - it.timestamp
                } ?: Long.MAX_VALUE

                val nextUpdateDelay = if (age < Constants.CacheLifetime.CACHE_VALIDITY_DURATION) {
                    _detailState.value = mapToUI(cachedData!!, context)
                    Constants.CacheLifetime.CACHE_VALIDITY_DURATION - age
                } else {
                    fetchWeatherData(cityName, context)
                    Constants.CacheLifetime.CACHE_VALIDITY_DURATION
                }

                delay(nextUpdateDelay)
            }
        }
    }

    fun stopRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRefresh()
    }
}