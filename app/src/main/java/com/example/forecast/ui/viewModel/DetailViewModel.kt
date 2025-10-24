package com.example.forecast.ui.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.core.utils.Constants
import com.example.forecast.R
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.data.dataclass.ForecastItem
import com.example.forecast.data.dataclass.ForecastMain
import com.example.forecast.data.dataclass.ForecastUI
import com.example.forecast.data.dataclass.ForecastWeather
import com.example.forecast.data.dataclass.Weather
import com.example.forecast.network.retrofit.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.get
import kotlin.math.round

sealed class DetailUIState {
    object Loading : DetailUIState()

    data class Success(
        val temperature: String,
        val iconUrl: String,
        val forecast: List<ForecastUI>
    ) : DetailUIState()

    data class Error(val message: String) : DetailUIState()
}

class DetailViewModel : ViewModel() {

    companion object {
        private const val SECONDS_TO_MILLIS = 1000L
        private const val WEATHER_ICON_URL = "https://openweathermap.org/img/wn/%s.png"
        private const val TAG = "DetailViewModel"
    }

    private val _detailState = MutableLiveData<DetailUIState>()
    val detailState: LiveData<DetailUIState> get() = _detailState

    private var refreshJob: Job? = null
    private var cityName: String? = null
    private var context: Context? = null

    private val observer =
        Observer<Map<String, WeatherRepository.CachedWeatherData>> { cachedDetails ->
            if (cityName == null || context == null) return@Observer

            val cachedData = cachedDetails[cityName]
            if (cachedData != null && cachedData.current != null && cachedData.forecast != null) {
                _detailState.value = mapToUI(cachedData, context!!)
            }
    }

    init {
        WeatherRepository.cachedWeatherLiveData.observeForever(observer)
    }

    private fun getApiKey(context: Context) : String {
        val apiKey = context.getString(R.string.weather_api_key)
        if (apiKey.isEmpty()) {
            throw IllegalStateException("Missing API key")
        }

        return apiKey
    }

    suspend fun loadCityDetail(cityName: String, context: Context, showLoading: Boolean = true) {
        this.cityName = cityName
        this.context = context
        if (showLoading) _detailState.value = DetailUIState.Loading

        val cachedData = WeatherRepository.getCachedDetails(cityName)
        val isCurrentValid = cachedData?.current != null
                && isCachedValidCurrent(cachedData.timestampCurrent)
        val isForecastValid = cachedData?.forecast != null
                && isCachedValidForecast(cachedData.timestampForecast)

        if (isCurrentValid && isForecastValid) {
            _detailState.value = mapToUI(cachedData!!, context)
            return
        }

        viewModelScope.launch {
            try {
                val apiKey = getApiKey(context)
                if (!isCurrentValid) {
                    val current = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                    WeatherRepository.setCachedCurrent(cityName,
                        current,
                        System.currentTimeMillis()
                    )
                }
                if (!isForecastValid) {
                    val forecast = RetrofitClient.weatherApi.getForecast(cityName, apiKey)
                    WeatherRepository.setCachedForecast(cityName,
                        forecast,
                        System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _detailState.value =
                    DetailUIState.Error(context.getString(R.string.error_fetch_current_weather))
            }
        }
    }

    private fun isCachedValidCurrent(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    private fun isCachedValidForecast(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    private fun mapToUI(
        cached: WeatherRepository.CachedWeatherData,
        context: Context
    ): DetailUIState.Success {
        val forecastList = cached.forecast?.let { groupForecastByDay(it) } ?: emptyList()

        val uiForecast = forecastList
            .take(6)
            .map {
                ForecastUI(
                    dayOfWeek = SimpleDateFormat("E", Locale("ru"))
                        .format(Date(it.dt * SECONDS_TO_MILLIS))
                        .uppercase(),
                    iconUrl = WEATHER_ICON_URL.format(it.weather[0].icon),
                    tempMax = context.getString(
                        R.string.temperature_format,
                        round(it.main.tempMax).toInt()
                    ),
                    tempMin = context.getString(
                        R.string.temperature_format,
                        round(it.main.tempMin).toInt()
                    )
                )
            }

        return DetailUIState.Success(
            temperature = context.getString(
                R.string.temperature_format,
                round(cached.current!!.main.temp).toInt()
            ),
            iconUrl = WEATHER_ICON_URL.format(cached.current!!.weather[0].icon),
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

    fun startRefresh(cityName: String, context: Context) {
        if (refreshJob?.isActive == true) return
        this.context = context

        refreshJob = viewModelScope.launch {
            while (isActive) {
                val cachedData = WeatherRepository.getCachedDetails(cityName)
                val ageCurrent = cachedData?.timestampCurrent?.let {
                    System.currentTimeMillis() - it
                } ?: Long.MAX_VALUE
                val ageForecast = cachedData?.timestampForecast?.let {
                    System.currentTimeMillis() - it
                } ?: Long.MAX_VALUE

                val needCurrent = ageCurrent >= Constants.CacheLifetime.CACHE_VALIDITY_DURATION
                        || cachedData?.current == null
                val needForecast = ageForecast >= Constants.CacheLifetime.CACHE_VALIDITY_DURATION
                        || cachedData?.forecast == null

                if (!needCurrent && !needForecast) {
                    if (cachedData != null) _detailState.value = mapToUI(cachedData, context)
                    delay(Constants.CacheLifetime.CACHE_VALIDITY_DURATION.coerceAtLeast(1000L))
                    continue
                }

                try {
                    val apiKey = getApiKey(context)
                    if (needCurrent) {
                        val current = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                        WeatherRepository.setCachedCurrent(
                            cityName,
                            current,
                            System.currentTimeMillis())
                    }
                    if (needForecast) {
                        val forecast = RetrofitClient.weatherApi.getForecast(cityName, apiKey)
                        WeatherRepository.setCachedForecast(
                            cityName,
                            forecast,
                            System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to refresh", e)
                }

                val remainingCurrent = Constants.CacheLifetime.CACHE_VALIDITY_DURATION -
                        (System.currentTimeMillis() -
                                (WeatherRepository.getTimestampCurrent(cityName) ?: 0L))
                val remainingForecast = Constants.CacheLifetime.CACHE_VALIDITY_DURATION -
                        (System.currentTimeMillis() -
                                (WeatherRepository.getTimestampForecast(cityName) ?: 0L))
                val minDelay = minOf(remainingCurrent, remainingForecast).coerceAtLeast(1000L)
                delay(minDelay)
            }
        }
    }

    fun stopRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        WeatherRepository.cachedWeatherLiveData.removeObserver(observer)
        stopRefresh()
    }
}