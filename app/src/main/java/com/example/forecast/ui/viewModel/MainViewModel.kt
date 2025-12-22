package com.example.forecast.ui.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    private var currentLoadCitiesJob: Job? = null
    private var addCitiesJob: Job? = null

    private var isLoadedStartData = false

    fun initData(context: Context) {
        if (isLoadedStartData) return

        isLoadedStartData = true
        Log.e("MainViewModel", "ПЕРВЫЙ ВХОД - loadCities")
        loadCities(context)
    }

    private fun loadCities(context: Context) {
        if (currentLoadCitiesJob?.isActive == true) {
            Log.e("MainViewModel", "Загрузка городов уже началась...")
            return
        }

        currentLoadCitiesJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины loadCitiesData")
            loadCitiesData(context)
            Log.e("MainViewModel", "Корутина loadCitiesData завершена")
        }
    }

    private suspend fun loadCitiesData(context: Context) {
        _uiState.postValue(MainUIState.Loading)

        val cityNames = WeatherRepository.getCities()
        val cities = mutableListOf<CurrentWeather>()

        try {
            for (name in cityNames) {
                val cached = WeatherRepository.getCachedDetails(name)

                if (cached?.current != null && isCachedValidCurrent(cached.timestampCurrent)) {
                    cities.add(cached.current)
                } else {
                    Log.e("MainViewModel", "Запрос API для получения текущей погоды")

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
            Log.e("MainViewModel", "Запрос API для текущей погоды провален - ошибка")
            _uiState.value = MainUIState.Error(context.getString(R.string.error_load_cities))
        }
    }

    private suspend fun fetchCurrentWeatherForCity(
        cityName: String
    ): CurrentWeather {
        return RetrofitClient.weatherApi.getCurrentWeather(cityName)
    }

    fun addNewCity(cityName: String, context: Context) {
        if (addCitiesJob?.isActive == true) {
            Log.e("MainViewModel", "Добавление города уже началось")
            return
        }

        addCitiesJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины addCity")
            addCity(cityName, context)
            Log.e("MainViewModel", "Корутина addCity завершена")
        }
    }

    private suspend fun addCity(cityName: String, context: Context) {
        val currentCities = WeatherRepository.getCities()

        try {
            if (currentCities.any { it.equals(cityName, ignoreCase = true) }) {
                _errorMessage.value = context.getString(R.string.error_city_already_added)
                return
            }

            delay(7_000)

            Log.e("MainViewModel", "Запрос API для нового города")
            val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(
                cityName,
                weather,
                System.currentTimeMillis()
            )

            val cities = WeatherRepository.getCities()
                .mapNotNull { WeatherRepository.getCachedDetails(it)?.current }

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Запрос API для нового города провален - ошибка")
            _uiState.value =
                MainUIState.Error(context.getString(R.string.error_load_cities))
        }
    }

    private fun isCachedValidCurrent(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    suspend fun removeCity(cityName: String, context: Context) {
        WeatherRepository.removeCity(cityName)

        viewModelScope.launch {
            loadCitiesData(context)
        }
    }

    fun startRefresh() {
        if (refreshJob?.isActive == true) {
            // Log.e("MainViewModel", "Refresh корутина уже запущена, return")
            return
        }

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

    fun refreshCitiesSwipe(context: Context) {
        viewModelScope.launch {
            val cityNames = WeatherRepository.getCities()
            if (cityNames.isEmpty()) {
                _uiState.value = MainUIState.Success(emptyList())
                return@launch
            }

            stopRefresh()
            try {
                val cities = mutableListOf<CurrentWeather>()

                for (name in cityNames) {
                    val weather = fetchCurrentWeatherForCity(name)
                    WeatherRepository.setCachedCurrent(
                        name,
                        weather,
                        System.currentTimeMillis()
                    )
                    cities.add(weather)
                }
                _uiState.value = MainUIState.Success(cities)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Запрос API для текущей погоды провален - ошибка")
                _uiState.value = MainUIState.Error(context.getString(R.string.error_load_cities))
            } finally {
                startRefresh()
            }
        }
    }

    fun onRetryButton(context: Context) {
        loadCities(context)
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