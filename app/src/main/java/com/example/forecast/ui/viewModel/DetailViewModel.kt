package com.example.forecast.ui.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.core.utils.Constants
import com.example.forecast.core.utils.Event
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.data.dataclass.forecast.ForecastWeatherUI
import com.example.forecast.network.retrofit.RetrofitClient
import com.example.forecast.ui.mapper.CurrentWeatherUiMapper
import com.example.forecast.ui.mapper.ForecastWeatherUiMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class DetailUIState {

    object Loading : DetailUIState()
    data class Success(
        val temperature: Float,
        val iconUrl: String,
        val forecast: List<ForecastWeatherUI>
    ) : DetailUIState()
    data class Error(val errorType: ErrorType) : DetailUIState()
}

sealed class RefreshDetailState {

    object Standard: RefreshDetailState()
    object Loading : RefreshDetailState()
}

sealed class UiEventDetails {

    object ErrorRefresh : UiEventDetails()
}

enum class ErrorType {
    FETCH_DETAILS
}

class DetailViewModel : ViewModel() {

    private val currentUiMapper = CurrentWeatherUiMapper()
    private var forecastUiMapper: ForecastWeatherUiMapper? = null

    private val _detailState = MutableLiveData<DetailUIState>()
    val detailState: LiveData<DetailUIState> get() = _detailState

    private val _refreshState = MutableLiveData<RefreshDetailState>()
    val refreshState: LiveData<RefreshDetailState> = _refreshState

    private val _events = MutableLiveData<Event<UiEventDetails>>()
    val events: LiveData<Event<UiEventDetails>> = _events

    private var isLoadedStartData = false
    private var loadCityDetailJob: Job? = null
    private var refreshSwipeJob: Job? = null
    private var wasLoadingWhenPaused = false
    private var restartRequiredLoadCityDetail = false
    private var restartRequiredRefreshSwipe = false

    private fun isCachedValidCurrent(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.Cache.CACHE_VALIDITY_DURATION_MS
    }

    private fun isCachedValidForecast(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.Cache.CACHE_VALIDITY_DURATION_MS
    }

    fun initData(cityName: String) {
        if (isLoadedStartData) return

        isLoadedStartData = true
        loadCityDetail(cityName)
    }

    private fun loadCityDetail(cityName: String) {
        if (loadCityDetailJob?.isActive == true) return

        loadCityDetailJob = viewModelScope.launch {
            _detailState.postValue(DetailUIState.Loading)
            loadCityDetailData(cityName)
        }
    }

    private suspend fun loadCityDetailData(cityName: String) {
        val cachedData = WeatherRepository.getCachedDetails(cityName)
        val isCurrentValid = cachedData?.current != null &&
                isCachedValidCurrent(cachedData.current.timestamp)
        val isForecastValid = cachedData?.forecast != null &&
                isCachedValidForecast(cachedData.forecast.timestamp)

        if (isCurrentValid && isForecastValid) {
            _detailState.postValue(buildSuccessState(cachedData!!))
            return
        }

        try {
            if (!isCurrentValid) {
                val currentDto = RetrofitClient.weatherApi.getCurrentWeather(cityName)
                WeatherRepository.setCachedCurrent(cityName, currentDto)
            }

            if (!isForecastValid) {
                val forecastDto = RetrofitClient.weatherApi.getForecast(cityName)
                WeatherRepository.setCachedForecast(cityName, forecastDto)
            }

            val updatedData = WeatherRepository.getCachedDetails(cityName)

            if (updatedData?.current == null || updatedData.forecast == null) {
                _detailState.postValue(
                    DetailUIState.Error(ErrorType.FETCH_DETAILS)
                )
                return
            }

            _detailState.postValue(buildSuccessState(updatedData))
        } catch (e: Exception) {
            _detailState.postValue(
                DetailUIState.Error(ErrorType.FETCH_DETAILS)
            )
        }
    }

    private fun buildSuccessState(
        cached: WeatherRepository.CachedWeatherData?
    ): DetailUIState.Success {

        if (forecastUiMapper == null) forecastUiMapper = ForecastWeatherUiMapper()

        val currentUi = currentUiMapper.map(cached!!.current!!)
        val forecastUi = forecastUiMapper!!.map(cached!!.forecast!!)

        return DetailUIState.Success(
            temperature = currentUi.temp,
            iconUrl = currentUi.iconUrl,
            forecast = forecastUi
        )
    }

    private fun onSwipeRefreshDetails(cityName: String) {
        if (refreshSwipeJob?.isActive == true) return

        _refreshState.value = RefreshDetailState.Loading

        refreshSwipeJob = viewModelScope.launch {
            try {
                refreshDetailsSwipe(cityName)
            } finally {
                _refreshState.value = RefreshDetailState.Standard
            }
        }
    }

    private suspend fun refreshDetailsSwipe(cityName: String) {
        try {
            val currentDto = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(cityName, currentDto)

            val forecastDto = RetrofitClient.weatherApi.getForecast(cityName)
            WeatherRepository.setCachedForecast(cityName, forecastDto)

            val updated = WeatherRepository.getCachedDetails(cityName)
            _detailState.postValue(buildSuccessState(updated))
        } catch (e: Exception) {
            _events.value = Event(UiEventDetails.ErrorRefresh)
        } finally {
            _refreshState.value = RefreshDetailState.Standard
        }
    }

    private fun cancelLoadingOnPause() {
        wasLoadingWhenPaused = true

        if (loadCityDetailJob?.isActive == true) {
            loadCityDetailJob?.cancel()
            loadCityDetailJob = null
            restartRequiredLoadCityDetail = true
        }

        if (refreshSwipeJob?.isActive == true) {
            refreshSwipeJob?.cancel()
            refreshSwipeJob = null
            restartRequiredRefreshSwipe = true
        }
    }

    private fun retryInterruptedLoad(cityName: String) {
        if (wasLoadingWhenPaused) {

            if (restartRequiredLoadCityDetail) {
                loadCityDetail(cityName)
                restartRequiredLoadCityDetail = false
            }

            if (restartRequiredRefreshSwipe) {
                onSwipeRefreshDetails(cityName)
                restartRequiredRefreshSwipe = false
            }
        }
    }

    fun onStopFragment(isChangingConfigurations: Boolean) {
        if ((loadCityDetailJob?.isActive == true || refreshSwipeJob?.isActive == true) &&
            !isChangingConfigurations) cancelLoadingOnPause()
    }

    fun onResumeFragment(cityName: String) {
        if (wasLoadingWhenPaused) retryInterruptedLoad(cityName)
    }

    fun onSwipeRefresh(cityName: String) {
        onSwipeRefreshDetails(cityName)
    }

    fun onRetryButton(cityName: String) {
        loadCityDetail(cityName)
    }
}