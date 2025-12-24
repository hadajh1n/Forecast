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
import kotlinx.coroutines.launch

sealed class MainUIState {

    object Loading : MainUIState()
    data class Success(val cities: List<CurrentWeather>) : MainUIState()
    data class Error(val message: String) : MainUIState()
}

sealed class CitiesState {

    object Standard: CitiesState()
    object Loading : CitiesState()
    object Error : CitiesState()
}

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableLiveData<MainUIState>()
    val uiState: LiveData<MainUIState> get() = _uiState

    private val _citiesState = MutableLiveData<CitiesState>()
    val citiesState: LiveData<CitiesState> = _citiesState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage : LiveData<String> get() = _errorMessage

    private var isLoadedStartData = false
    private var isOfflineMode = false

    private var loadCitiesJob: Job? = null
    private var addCitiesJob: Job? = null
    private var refreshSwipeJob: Job? = null
    private var refreshBackgroundJob: Job? = null
    private var wasLoadingWhenPaused = false
    private var restartRequiredLoadCities = false
    private var restartRequiredRefreshSwipe = false

    private fun isCachedValidCurrent(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    fun initData(context: Context) {
        if (isLoadedStartData) return

        isLoadedStartData = true
        loadCities(context)
    }

    private fun loadCities(context: Context) {
        if (loadCitiesJob?.isActive == true) {
            Log.e("MainViewModel", "Загрузка городов уже началась...")
            return
        }

        loadCitiesJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины loadCitiesData")
            _uiState.postValue(MainUIState.Loading)
            loadCitiesData(context)
            Log.e("MainViewModel", "Корутина loadCitiesData завершена")
        }
    }

    private suspend fun loadCitiesData(context: Context) {
        val cachedCities = WeatherRepository.getMemoryCities()
        val cities = mutableListOf<CurrentWeather>()

        if (cachedCities.isEmpty()) {
            Log.e("MainViewModel", "loadCitiesData - пустой список")
            _uiState.postValue(MainUIState.Success(emptyList()))
            return
        }

        try {
            delay(7_000)

            for (name in cachedCities) {
                val cached = WeatherRepository.getCachedDetails(name)

                if (cached?.current != null && isCachedValidCurrent(cached.timestampCurrent)) {
                    Log.e("MainViewModel", "Кэш уже есть, выводим города")
                    cities.add(cached.current)
                } else {
                    Log.e("MainViewModel", "Запрос API для получения текущей погоды")

                    val weather = RetrofitClient.weatherApi.getCurrentWeather(name)
                    WeatherRepository.setCachedCurrent(
                        name,
                        weather,
                        System.currentTimeMillis()
                    )
                    cities.add(weather)
                }
            }

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Запрос API для текущей погоды провален - ошибка")

            if (cachedCities.isNotEmpty()) {
                _uiState.postValue(MainUIState.Success(cities))
                return
            }

            _uiState.postValue(MainUIState.Error(context.getString(R.string.error_load_cities)))
            isOfflineMode = true
        }
    }

    private fun addNewCity(cityName: String, context: Context) {
        if (addCitiesJob?.isActive == true) {
            Log.e("MainViewModel", "Добавление города уже началось")
            return
        }

        _citiesState.value = CitiesState.Loading

        addCitiesJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины addCity")
            addCity(cityName, context)
            Log.e("MainViewModel", "Корутина addCity завершена")
        }
    }

    private suspend fun addCity(cityName: String, context: Context) {
        val currentCities = WeatherRepository.getMemoryCities()

        try {
            delay(7_000)

            if (currentCities.any { it.equals(cityName, ignoreCase = true) }) {
                _errorMessage.postValue(context.getString(R.string.error_city_already_added))
                _citiesState.postValue(CitiesState.Standard)
                return
            }

            Log.e("MainViewModel", "Запрос API для нового города")
            val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(
                cityName,
                weather,
                System.currentTimeMillis()
            )

            val cities = WeatherRepository.getMemoryCities()
                .mapNotNull { WeatherRepository.getCachedDetails(it)?.current }

            _uiState.postValue(MainUIState.Success(cities))
            _citiesState.postValue(CitiesState.Standard)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Запрос API для нового города провален - ошибка")
            _uiState.postValue(MainUIState.Error(context.getString(R.string.error_load_cities)))
            _citiesState.postValue(CitiesState.Error)
        }
    }

    suspend fun removeCity(cityName: String, context: Context) {
        WeatherRepository.removeCity(cityName)

        viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины removeCity")
            loadCitiesData(context)
            Log.e("MainViewModel", "Корутина removeCity завершена")
        }
    }

    private fun refreshCities(context: Context) {
        if (refreshSwipeJob?.isActive == true) {
            Log.e("DetailViewModel", "Refresh уже начался...")
            return
        }

        refreshSwipeJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины refreshSwipe")
            refreshCitiesSwipe(context)
            Log.e("MainViewModel", "Корутина refreshSwipe завершена")
        }
    }

    private suspend fun refreshCitiesSwipe(context: Context) {
        val cityNames = WeatherRepository.getMemoryCities()

        if (cityNames.isEmpty()) {
            _uiState.postValue(MainUIState.Success(emptyList()))
            return
        }

        try {
            delay(7_000)

            val cities = mutableListOf<CurrentWeather>()

            for (name in cityNames) {
                val weather = RetrofitClient.weatherApi.getCurrentWeather(name)
                WeatherRepository.setCachedCurrent(
                    name,
                    weather,
                    System.currentTimeMillis()
                )
                cities.add(weather)
            }
            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Запрос API для текущей погоды провален - ошибка")
            _uiState.postValue(MainUIState.Error(context.getString(R.string.error_load_cities)))
        }
    }

//    private fun refreshBackground() {
//        if (refreshBackgroundJob?.isActive == true) return
//
//        refreshBackgroundJob = viewModelScope.launch {
//            Log.e("MainViewModel", "Запуск корутины refreshBackground")
//            startRefreshBackground()
//            Log.e("MainViewModel", "Корутина refreshBackground завершена")
//        }
//    }

    private suspend fun startRefreshBackground() {
        while (refreshBackgroundJob?.isActive == true) {
            val cityNames = WeatherRepository.getMemoryCities()
            var minDelay = Constants.CacheLifetime.CACHE_VALIDITY_DURATION
            var dataChanged = false

            for (name in cityNames) {
                val timestamp = WeatherRepository.getTimestampCurrent(name) ?: 0L
                val age = System.currentTimeMillis() - timestamp

                if (age >= Constants.CacheLifetime.CACHE_VALIDITY_DURATION) {
                    try {
                        val weather = RetrofitClient.weatherApi.getCurrentWeather(name)
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
                _uiState.postValue(MainUIState.Success(cities))
            }

            delay(minDelay)
        }
    }

    private fun cancelLoadingOnPause() {
        wasLoadingWhenPaused = true

        if (loadCitiesJob?.isActive == true) {
            Log.e("MainViewModel", "Отмена корутины loadCitiesData")
            loadCitiesJob?.cancel()
            loadCitiesJob = null
            restartRequiredLoadCities = true
        }

        if (refreshSwipeJob?.isActive == true) {
            Log.e("MainViewModel", "Отмена корутины Refresh")
            refreshSwipeJob?.cancel()
            refreshSwipeJob = null
            restartRequiredRefreshSwipe = true
        }
    }

    private fun retryInterruptedLoad(context: Context) {
        if (wasLoadingWhenPaused) {

            if (restartRequiredLoadCities) {
                Log.e("MainViewModel", "Перезапуск корутины loadCitiesData")
                loadCities(context)
                restartRequiredLoadCities = false
            }

            if (restartRequiredRefreshSwipe) {
                Log.e("MainViewModel", "Перезапуск корутины Refresh")
                refreshCities(context)
                restartRequiredRefreshSwipe = false
            }
        }
    }

    fun onStopFragment(isChangingConfigurations: Boolean) {
        if ((loadCitiesJob?.isActive == true || refreshSwipeJob?.isActive == true) &&
            !isChangingConfigurations) cancelLoadingOnPause()
    }

    fun onResumeFragment(context: Context) {
        if (wasLoadingWhenPaused) retryInterruptedLoad(context)
    }

    fun onAddNewCity(cityName: String, context: Context) {
        addNewCity(cityName, context)
    }

    fun onSwipeRefresh(context: Context) {
        refreshCities(context)
    }

    fun onRefreshBackground() {
//        refreshBackground()
    }

    fun onRetryButton(context: Context) {
        loadCities(context)
    }
}