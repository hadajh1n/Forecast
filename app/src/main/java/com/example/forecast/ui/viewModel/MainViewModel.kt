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
import com.example.forecast.data.dataclass.current.CurrentWeatherUI
import com.example.forecast.network.retrofit.RetrofitClient
import com.example.forecast.ui.mapper.CurrentWeatherUiMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class MainUIState {

    object Loading : MainUIState()
    data class Success(val cities: List<CurrentWeatherUI>) : MainUIState()
    data class Error(val message: String) : MainUIState()
}

sealed class CitiesState {

    object Standard : CitiesState()
    object Loading : CitiesState()
    object Error : CitiesState()
}

sealed class RefreshCityState {

    object Standard : RefreshCityState()
    object Loading : RefreshCityState()
}

class MainViewModel : ViewModel() {

    private val currentUiMapper = CurrentWeatherUiMapper()

    private val _uiState = MutableLiveData<MainUIState>()
    val uiState: LiveData<MainUIState> get() = _uiState

    private val _citiesState = MutableLiveData<CitiesState>()
    val citiesState: LiveData<CitiesState> = _citiesState

    private val _refreshState = MutableLiveData<RefreshCityState>()
    val refreshState: LiveData<RefreshCityState> = _refreshState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    private var isLoadedStartData = false
    private var isOfflineMode = false

    private var loadCitiesJob: Job? = null
    private var addCitiesJob: Job? = null
    private var refreshSwipeJob: Job? = null
    private var refreshBackgroundJob: Job? = null
    private var wasLoadingWhenPaused = false
    private var restartRequiredLoadCities = false
    private var restartRequiredRefreshSwipe = false

    private var activeAddCities = 0
    private val mutex = Mutex()

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
        val cities = mutableListOf<CurrentWeatherUI>()

        if (cachedCities.isEmpty()) {
            _uiState.postValue(MainUIState.Success(emptyList()))
            return
        }

        try {
            delay(3_000)

            for (city in cachedCities) {
                val cached = WeatherRepository.getCachedDetails(city)

                val cacheCurrent = if (cached?.current != null &&
                    isCachedValidCurrent(cached.current.timestamp)
                ) {
                    Log.e("MainViewModel", "Кэш не пустой и данные валидны, запрос API для $city не требуется")
                    cached.current
                } else {
                    Log.e("MainViewModel", "Запрос API для города $city")
                    val weatherDto = RetrofitClient.weatherApi.getCurrentWeather(city)
                    WeatherRepository.setCachedCurrent(city, weatherDto)
                    WeatherRepository.getCachedDetails(city)?.current
                }

                cacheCurrent?.let { cities += currentUiMapper.map(it) }
            }

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            if (cachedCities.isNotEmpty()) {
                Log.e("MainViewModel", "Ошибка API - кэш не пустой")
                _uiState.postValue(
                    MainUIState.Success(
                    cachedCities.mapNotNull {
                        WeatherRepository.getCachedDetails(it)?.current?.let(currentUiMapper::map)
                    }
                ))
                return
            }
            _uiState.postValue(MainUIState.Error(context.getString(R.string.error_load_cities)))
            isOfflineMode = true
        }
    }

    private fun addNewCity(cityName: String, context: Context) {
        addCitiesJob = viewModelScope.launch {
            Log.e("MainViewModel", "+1 город к загрузке")
            activeAddCities++
            Log.e("MainViewModel", "Запуск корутины addCity")
            addCity(cityName, context)
            Log.e("MainViewModel", "Корутина addCity завершена")
        }
    }

    private suspend fun addCity(cityName: String, context: Context) = mutex.withLock {
        _citiesState.value = CitiesState.Loading
        val currentCities = WeatherRepository.getMemoryCities()

        if (currentCities.any { it.equals(cityName, ignoreCase = true) }) {
            _errorMessage.postValue(context.getString(R.string.error_city_already_added))
            _citiesState.postValue(CitiesState.Standard)
            return@withLock
        }

        try {
            delay(3_000)

            Log.e("MainViewModel", "Запрос API для нового города")
            val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(cityName, weather)

            val cities = WeatherRepository.getMemoryCities()
                .mapNotNull { WeatherRepository.getCachedDetails(it)?.current }
                .map(currentUiMapper::map)

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            _uiState.postValue(MainUIState.Error(context.getString(R.string.error_load_cities)))
            _citiesState.postValue(CitiesState.Error)
        } finally {
            activeAddCities--
            _citiesState.postValue(
                if (activeAddCities > 0) CitiesState.Loading else CitiesState.Standard
            )
        }
    }

    private suspend fun removeCity(cityName: String, context: Context) {
        WeatherRepository.removeCity(cityName)

        viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины removeCity")
            loadCitiesData(context)
            Log.e("MainViewModel", "Корутина removeCity завершена")
        }
    }

    private fun onSwipeRefreshCities(context: Context) {
        if (refreshSwipeJob?.isActive == true) {
            Log.e("MainViewModel", "Refresh уже начался...")
            return
        }

        _refreshState.value = RefreshCityState.Loading

        refreshSwipeJob = viewModelScope.launch {
            try {
                Log.e("MainViewModel", "Запуск корутины refreshSwipe")
                refreshCitiesSwipe(context)
                Log.e("MainViewModel", "Корутина refreshSwipe завершена")
            } finally {
                _refreshState.value = RefreshCityState.Standard
            }
        }
    }

    private suspend fun refreshCitiesSwipe(context: Context) {
        val cachedCities = WeatherRepository.getMemoryCities()
        val cities = mutableListOf<CurrentWeatherUI>()

        if (cachedCities.isEmpty()) {
            _uiState.postValue(MainUIState.Success(emptyList()))
            return
        }

        try {
            delay(3_000)

            for (city in cachedCities) {
                Log.e("MainViewModel", "Pull-to-refresh - Запрос API для города $city")
                val weatherDto = RetrofitClient.weatherApi.getCurrentWeather(city)
                WeatherRepository.setCachedCurrent(city, weatherDto)

                WeatherRepository.getCachedDetails(city)?.current?.let {
                    cities += currentUiMapper.map(it)
                }
            }

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            if (cachedCities.isNotEmpty()) {
                Log.e("MainViewModel", "Ошибка refresh API - кэш не пустой")
                _uiState.postValue(
                    MainUIState.Success(
                        cachedCities.mapNotNull {
                            WeatherRepository.getCachedDetails(it)?.current?.let(currentUiMapper::map)
                        }
                    )
                )
                return
            }
            _uiState.postValue(MainUIState.Error(context.getString(R.string.error_load_cities)))
            isOfflineMode = true
        }
    }

    private fun refreshBackground(cityName: String, context: Context) {
        if (refreshBackgroundJob?.isActive == true) return

        refreshBackgroundJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины refreshBackground")
            startRefreshBackground(cityName, context)
            Log.e("MainViewModel", "Корутина refreshBackground завершена")
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
                Log.e("DetailViewModel", "Ошибка фонового обновления", e)
            }
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
                onSwipeRefreshCities(context)
                restartRequiredRefreshSwipe = false
            }
        }
    }

    fun onStopFragment(isChangingConfigurations: Boolean) {
        if ((loadCitiesJob?.isActive == true || refreshSwipeJob?.isActive == true) &&
            !isChangingConfigurations
        ) cancelLoadingOnPause()
    }

    fun onResumeFragment(context: Context) {
        if (wasLoadingWhenPaused) retryInterruptedLoad(context)
    }

    fun onAddNewCity(cityName: String, context: Context) {
        addNewCity(cityName, context)
    }

    suspend fun requestRemoveCity(cityName: String, context: Context) {
        removeCity(cityName, context)
    }

    fun onSwipeRefresh(context: Context) {
        onSwipeRefreshCities(context)
    }

    fun onRefreshBackground() {
//        refreshBackground()
    }

    fun onRetryButton(context: Context) {
        loadCities(context)
    }
}