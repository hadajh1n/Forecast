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
import com.example.forecast.data.dataclass.forecast.ForecastWeatherUI
import com.example.forecast.network.retrofit.RetrofitClient
import com.example.forecast.ui.mapper.CurrentWeatherUiMapper
import com.example.forecast.ui.mapper.ForecastWeatherUiMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round

sealed class DetailUIState {
    object Loading : DetailUIState()

    data class Success(
        val temperature: String,
        val iconUrl: String,
        val forecast: List<ForecastWeatherUI>
    ) : DetailUIState()

    data class Error(val message: String) : DetailUIState()
}

sealed class RefreshDetailState {

    object Standard: RefreshDetailState()
    object Loading : RefreshDetailState()
}

class DetailViewModel : ViewModel() {

    private val currentUiMapper = CurrentWeatherUiMapper()
    private var forecastUiMapper: ForecastWeatherUiMapper? = null

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

        Log.e("MainViewModel", "Запрос данных о деталях погоды")
        isLoadedStartData = true
        loadCityDetail(cityName, context)
    }

    private fun loadCityDetail(cityName: String, context: Context) {
        if (loadCityDetailJob?.isActive == true) return

        loadCityDetailJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины loadCityDetail")
            _detailState.postValue(DetailUIState.Loading)
            loadCityDetailData(cityName, context)
            Log.e("MainViewModel", "Корутина loadCityDetail завершена")
        }
    }

    private suspend fun loadCityDetailData(cityName: String, context: Context) {
        this.cityName = cityName
        this.context = context

        val cachedData = WeatherRepository.getCachedDetails(cityName)
        val isCurrentValid = cachedData?.current != null &&
                isCachedValidCurrent(cachedData.current.timestamp)
        val isForecastValid = cachedData?.forecast != null &&
                isCachedValidForecast(cachedData.forecast.timestamp)

        if (isCurrentValid && isForecastValid) {
            _detailState.postValue(buildSuccessState(cachedData!!, context))
            return
        }

        try {
            delay(3_000)

            if (isCurrentValid && isForecastValid) {
                _detailState.postValue(buildSuccessState(cachedData!!, context))
                return
            }

            if (!isCurrentValid) {
                Log.e("MainViewModel", "Запрос API для current")
                val currentDto = RetrofitClient.weatherApi.getCurrentWeather(cityName)
                WeatherRepository.setCachedCurrent(cityName, currentDto)
            }

            if (!isForecastValid) {
                Log.e("MainViewModel", "Запрос API для forecast")
                val forecastDto = RetrofitClient.weatherApi.getForecast(cityName)
                WeatherRepository.setCachedForecast(cityName, forecastDto)
            }

            val updatedData = WeatherRepository.getCachedDetails(cityName)
                ?: return _detailState.postValue(
                    DetailUIState.Error(context.getString(R.string.error_fetch_current_weather))
                )

            if (updatedData.current == null || updatedData.forecast == null) {
                _detailState.postValue(
                    DetailUIState.Error(context.getString(R.string.error_fetch_current_weather))
                )
                return
            }

            _detailState.postValue(buildSuccessState(updatedData, context))
        } catch (e: Exception) {
            _detailState.postValue(
                DetailUIState.Error(context.getString(R.string.error_fetch_current_weather))
            )
        }
    }

    private fun buildSuccessState(
        cached: WeatherRepository.CachedWeatherData,
        context: Context
    ): DetailUIState.Success {

        if (forecastUiMapper == null) {
            forecastUiMapper = ForecastWeatherUiMapper(context)
        }

        val currentUi = currentUiMapper.map(cached.current!!)
        val forecastUi = forecastUiMapper!!.map(cached.forecast!!)

        return DetailUIState.Success(
            temperature = context.getString(
                R.string.temperature_format,
                round(currentUi.temp).toInt()
            ),
            iconUrl = currentUi.iconUrl,
            forecast = forecastUi
        )
    }

    private fun onSwipeRefreshDetails(cityName: String, context: Context) {
        if (refreshSwipeJob?.isActive == true) {
            Log.e("MainViewModel", "Refresh уже начался...")
            return
        }

        _refreshState.value = RefreshDetailState.Loading

        refreshSwipeJob = viewModelScope.launch {
            try {
                Log.e("MainViewModel", "Запуск корутины Refresh")
                refreshDetailsSwipe(cityName, context)
                Log.e("MainViewModel", "Корутина Refresh завершена")
            } finally {
                startRefreshBackground(cityName, context)
                _refreshState.value = RefreshDetailState.Standard
            }
        }
    }

    private suspend fun refreshDetailsSwipe(cityName: String, context: Context) {
        try {
            delay(7_000)

            val currentDto = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(cityName, currentDto)

            val forecastDto = RetrofitClient.weatherApi.getForecast(cityName)
            WeatherRepository.setCachedForecast(cityName, forecastDto)

            val updated = WeatherRepository.getCachedDetails(cityName)
            if (updated?.current != null && updated.forecast != null) {
                _detailState.postValue(buildSuccessState(updated, context))
            }
        } catch (e: Exception) {
            _messageEvent.postValue(Event(context.getString(R.string.error_pull_to_refresh)))
        } finally {
            _refreshState.value = RefreshDetailState.Standard
        }
    }

    private fun refreshBackground(cityName: String, context: Context) {
        if (refreshBackgroundJob?.isActive == true) return

        refreshBackgroundJob = viewModelScope.launch {
            startRefreshBackground(cityName, context)
        }
    }

    private suspend fun startRefreshBackground(cityName: String, context: Context) {
        while (true) {
            delay(Constants.CacheLifetime.BACKGROUND_UPDATE_INTERVAL)
            try {
                val currentDto = RetrofitClient.weatherApi.getCurrentWeather(cityName)
                WeatherRepository.setCachedCurrent(cityName, currentDto)
                val forecastDto = RetrofitClient.weatherApi.getForecast(cityName)
                WeatherRepository.setCachedForecast(cityName, forecastDto)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка фонового обновления", e)
            }
        }
    }

    private fun cancelLoadingOnPause() {
        wasLoadingWhenPaused = true

        if (loadCityDetailJob?.isActive == true) {
            Log.e("MainViewModel", "Отмена корутины loadCitiesDetail")
            loadCityDetailJob?.cancel()
            loadCityDetailJob = null
            restartRequiredLoadCityDetail = true
        }

        if (refreshSwipeJob?.isActive == true) {
            Log.e("MainViewModel", "Отмена корутины Refresh")
            refreshSwipeJob?.cancel()
            refreshSwipeJob = null
            restartRequiredRefreshSwipe = true
        }
    }

    private fun retryInterruptedLoad(cityName: String, context: Context) {
        if (wasLoadingWhenPaused) {

            if (restartRequiredLoadCityDetail) {
                Log.e("MainViewModel", "Перезапуск корутины loadCitiesDetail")
                loadCityDetail(cityName, context)
                restartRequiredLoadCityDetail = false
            }

            if (restartRequiredRefreshSwipe) {
                Log.e("MainViewModel", "Перезапуск корутины Refresh")
                onSwipeRefreshDetails(cityName, context)
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
        onSwipeRefreshDetails(cityName, context)
    }

    fun onRefreshBackground(cityName: String, context: Context) {
//        refreshBackground(cityName, context)
    }

    fun onRetryButton(cityName: String, context: Context) {
        loadCityDetail(cityName, context)
    }
}