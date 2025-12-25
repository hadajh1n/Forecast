package com.example.forecast.ui.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.core.utils.Constants
import com.example.forecast.R
import com.example.forecast.core.utils.Event
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.data.dataclass.ForecastItem
import com.example.forecast.data.dataclass.ForecastMain
import com.example.forecast.data.dataclass.ForecastUI
import com.example.forecast.data.dataclass.ForecastWeather
import com.example.forecast.data.dataclass.Weather
import com.example.forecast.network.retrofit.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

sealed class RefreshDetailState {

    object Standard: RefreshDetailState()
    object Loading : RefreshDetailState()
}

class DetailViewModel : ViewModel() {

    companion object {
        private const val SECONDS_TO_MILLIS = 1000L
        private const val WEATHER_ICON_URL = "https://openweathermap.org/img/wn/%s.png"
        private const val TAG = "DetailViewModel"
    }

    private val _detailState = MutableLiveData<DetailUIState>()
    val detailState: LiveData<DetailUIState> get() = _detailState

    private val _refreshState = MutableLiveData<RefreshDetailState>()
    val refreshState: LiveData<RefreshDetailState> = _refreshState

    private val _messageEvent = MutableLiveData<Event<String>>()
    val messageEvent: LiveData<Event<String>> get() = _messageEvent

    private var cityName: String? = null
    private var context: Context? = null
    private var isLoadedStartData = false

    private var loadCityDetailJob: Job? = null
    private var refreshSwipeJob: Job? = null
    private var refreshBackgroundJob: Job? = null
    private var wasLoadingWhenPaused = false
    private var restartRequiredLoadCityDetail = false
    private var restartRequiredRefreshSwipe = false

    private fun isCachedValidCurrent(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    private fun isCachedValidForecast(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    fun initData(cityName: String, context: Context) {
        if (isLoadedStartData) return

        Log.e("DetailViewModel", "Запрос данных о деталях погоды")
        isLoadedStartData = true
        loadCityDetail(cityName, context)
    }

    private fun loadCityDetail(cityName: String, context: Context) {
        if (loadCityDetailJob?.isActive == true) return

        loadCityDetailJob = viewModelScope.launch {
            Log.e("DetailViewModel", "Запуск корутины loadCityDetail")
            _detailState.postValue(DetailUIState.Loading)
            loadCityDetailData(cityName, context)
            Log.e("DetailViewModel", "Корутина loadCityDetail завершена")
        }
    }

    private suspend fun loadCityDetailData(cityName: String, context: Context) {
        this.cityName = cityName
        this.context = context

        val cachedData = WeatherRepository.getCachedDetails(cityName)
        val isCurrentValid = cachedData?.current != null
                && isCachedValidCurrent(cachedData.timestampCurrent)
        val isForecastValid = cachedData?.forecast != null
                && isCachedValidForecast(cachedData.timestampForecast)

        if (isCurrentValid && isForecastValid) {
            _detailState.postValue(mapToUI(cachedData!!, context))
            return
        }

        try {
            delay(7_000)

            if (!isCurrentValid) {
                Log.e("DetailViewModel", "Запрос API для current")
                val current = RetrofitClient.weatherApi.getCurrentWeather(cityName)
                WeatherRepository.setCachedCurrent(
                    cityName,
                    current,
                    System.currentTimeMillis()
                )
            }
            if (!isForecastValid) {
                Log.e("DetailViewModel", "Запрос API для forecast")
                val forecast = RetrofitClient.weatherApi.getForecast(cityName)
                WeatherRepository.setCachedForecast(
                    cityName,
                    forecast,
                    System.currentTimeMillis()
                )
            }

            val updatedData = WeatherRepository.getCachedDetails(cityName)
            if (updatedData?.current != null && updatedData.forecast != null) {
                _detailState.postValue(mapToUI(updatedData, context))
            } else {
                _detailState.postValue(
                    DetailUIState.Error(context.getString(R.string.error_fetch_current_weather))
                )

            }
        } catch (e: Exception) {
            _detailState.postValue(
                DetailUIState.Error(context.getString(R.string.error_fetch_current_weather))
            )
        }
    }

    private fun refreshDetails(cityName: String, context: Context) {
        if (refreshSwipeJob?.isActive == true) {
            Log.e("DetailViewModel", "Refresh уже начался...")
            return
        }

        _refreshState.value = RefreshDetailState.Loading

        refreshSwipeJob = viewModelScope.launch {
            try {
                Log.e("DetailViewModel", "Запуск корутины Refresh")
                refreshDetailsSwipe(cityName, context)
                Log.e("DetailViewModel", "Корутина Refresh завершена")
            } finally {
                startRefreshBackground(cityName, context)
                _refreshState.value = RefreshDetailState.Standard
            }
        }
    }

    private suspend fun refreshDetailsSwipe(cityName: String, context: Context) {
        this.cityName = cityName
        this.context = context

        try {
            delay(7_000)

            val current = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(
                cityName,
                current,
                System.currentTimeMillis()
            )

            val forecast = RetrofitClient.weatherApi.getForecast(cityName)
            WeatherRepository.setCachedForecast(
                cityName,
                forecast,
                System.currentTimeMillis()
            )

            WeatherRepository.getCachedDetails(cityName)?.let { cachedData ->
                if (cachedData.current != null && cachedData.forecast != null) {
                    _detailState.value = mapToUI(cachedData, context)
                } else {
                    _detailState.value =
                        DetailUIState.Error(
                            context.getString(R.string.error_fetch_current_weather)
                        )
                }
            }
        } catch (e: Exception) {
            _messageEvent.postValue(Event(context.getString(R.string.error_pull_to_refresh)))
        }
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
                        weather = listOf(Weather(icon = icon, description = ""))
                    )
                )
            }
        }

        return dailyForecasts
    }

//    private fun refreshBackground(cityName: String, context: Context) {
//        if (refreshBackgroundJob?.isActive == true) return
//
//        refreshBackgroundJob = viewModelScope.launch {
//            startRefreshBackground(cityName, context)
//        }
//    }

    private suspend fun startRefreshBackground(cityName: String, context: Context) {
        this.context = context

        while (refreshBackgroundJob?.isActive == true) {
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
                if (needCurrent) {
                    val current = RetrofitClient.weatherApi.getCurrentWeather(cityName)
                    WeatherRepository.setCachedCurrent(
                        cityName,
                        current,
                        System.currentTimeMillis())
                }
                if (needForecast) {
                    val forecast = RetrofitClient.weatherApi.getForecast(cityName)
                    WeatherRepository.setCachedForecast(
                        cityName,
                        forecast,
                        System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh", e)
            }

            val updatedData = WeatherRepository.getCachedDetails(cityName)
            if (updatedData?.current != null && updatedData.forecast != null) {
                _detailState.value = mapToUI(updatedData, context)
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

    private fun cancelLoadingOnPause() {
        wasLoadingWhenPaused = true

        if (loadCityDetailJob?.isActive == true) {
            Log.e("DetailViewModel", "Отмена корутины loadCitiesDetail")
            loadCityDetailJob?.cancel()
            loadCityDetailJob = null
            restartRequiredLoadCityDetail = true
        }

        if (refreshSwipeJob?.isActive == true) {
            Log.e("DetailViewModel", "Отмена корутины Refresh")
            refreshSwipeJob?.cancel()
            refreshSwipeJob = null
            restartRequiredRefreshSwipe = true
        }
    }

    private fun retryInterruptedLoad(cityName: String, context: Context) {
        if (wasLoadingWhenPaused) {

            if (restartRequiredLoadCityDetail) {
                Log.e("DetailViewModel", "Перезапуск корутины loadCitiesDetail")
                loadCityDetail(cityName, context)
                restartRequiredLoadCityDetail = false
            }

            if (restartRequiredRefreshSwipe) {
                Log.e("DetailViewModel", "Перезапуск корутины Refresh")
                refreshDetails(cityName, context)
                restartRequiredRefreshSwipe = false
            }
        }
    }

    fun onStopFragment(isChangingConfigurations: Boolean) {
        if ((loadCityDetailJob?.isActive == true || refreshSwipeJob?.isActive == true) &&
            !isChangingConfigurations) cancelLoadingOnPause()
    }

    fun onResumeFragment(cityName: String, context: Context) {
        if (wasLoadingWhenPaused) retryInterruptedLoad(cityName, context)
    }

    fun onSwipeRefresh(cityName: String, context: Context) {
        refreshDetails(cityName, context)
    }

    fun onRefreshBackground(cityName: String, context: Context) {
//        refreshBackground(cityName, context)
    }

    fun onRetryButton(cityName: String, context: Context) {
        loadCityDetail(cityName, context)
    }
}