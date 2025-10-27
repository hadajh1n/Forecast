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
import com.example.forecast.data.dataclass.CurrentWeather
import com.example.forecast.network.retrofit.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class MainUIState {
    object Loading : MainUIState()
    data class Success(val cities: List<CurrentWeather>) : MainUIState()
    data class Error(val message: String) : MainUIState()
}

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableLiveData<MainUIState>()
    val uiState: LiveData<MainUIState> get() = _uiState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage : LiveData<String> get() = _errorMessage

    private var refreshJob: Job? = null

    private val observer =
        Observer<Map<String, WeatherRepository.CachedWeatherData>> { cachedDetails ->
            val cities = cachedDetails.values.mapNotNull { it.current }
                .sortedBy { cachedDetails[it.name]?.orderIndex }
            _uiState.value = MainUIState.Success(cities)
    }

    fun addCity(cityName: String, context: Context) {
        viewModelScope.launch {
            try {
                val currentCities = WeatherRepository.getCities()
                if (currentCities.any { it.equals(cityName, ignoreCase = true) }) {
                    _errorMessage.value = context.getString(R.string.error_city_already_added)
                    return@launch
                }

                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName)
                WeatherRepository.setCachedCurrent(
                    cityName,
                    weather,
                    System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _uiState.value =
                        MainUIState.Error(context.getString(R.string.error_load_cities))
            }
        }
    }

    private suspend fun fetchCurrentWeatherForCity(
        cityName: String
    ): CurrentWeather {
        return RetrofitClient.weatherApi.getCurrentWeather(cityName)
    }

    private suspend fun loadCitiesData(context: Context, showLoading: Boolean = false) {
        if (showLoading) _uiState.value = MainUIState.Loading

        try {
            val cityNames = WeatherRepository.getCities()
            val cities = mutableListOf<CurrentWeather>()

            for (name in cityNames) {
                val cached = WeatherRepository.getCachedDetails(name)
                if (cached?.current != null && isCachedValidCurrent(cached.timestampCurrent)) {
                    cities.add(cached.current)
                } else {
                    val weather = fetchCurrentWeatherForCity(name)
                    WeatherRepository.setCachedCurrent(
                        name,
                        weather,
                        System.currentTimeMillis()
                    )
                    cities.add(weather)
                }
            }

            _uiState.value = MainUIState.Success(cities)
        } catch (e: Exception) {
            _uiState.value = MainUIState.Error(context.getString(R.string.error_load_cities))
        }
    }

    fun loadCities(context: Context) {
        viewModelScope.launch {
            loadCitiesData(context, showLoading = true)
            WeatherRepository.cachedWeatherLiveData.observeForever(observer)
        }
    }

    private fun isCachedValidCurrent(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    suspend fun removeCity(cityName: String, context: Context) {
        WeatherRepository.removeCity(cityName)

        viewModelScope.launch {
            loadCitiesData(context, showLoading = false)
        }
    }

    fun startRefresh(context: Context) {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            while (isActive) {
                val cityNames = WeatherRepository.getCities()
                var minDelay = Constants.CacheLifetime.CACHE_VALIDITY_DURATION
                var dataChanged = false

                for (name in cityNames) {
                    val timestamp = WeatherRepository.getTimestampCurrent(name) ?: 0L
                    val age = System.currentTimeMillis() - timestamp
                    if (age >= Constants.CacheLifetime.CACHE_VALIDITY_DURATION) {
                        try {
                            val weather = fetchCurrentWeatherForCity(name)
                            WeatherRepository.setCachedCurrent(
                                name,
                                weather,
                                System.currentTimeMillis()
                            )
                            dataChanged = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to refresh city: $name", e)
                        }
                    }
                    val remaining =
                        Constants.CacheLifetime.CACHE_VALIDITY_DURATION -
                                (System.currentTimeMillis() - timestamp)
                    if (remaining < minDelay) minDelay = remaining.coerceAtLeast(1000L)
                }

                if (dataChanged) {
                    val cities =
                        cityNames.mapNotNull { WeatherRepository.getCachedDetails(it)?.current }
                    _uiState.value = MainUIState.Success(cities)
                }

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