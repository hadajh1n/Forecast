package com.example.forecast.ui.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.core.utils.Constants
import com.example.forecast.core.utils.Event
import com.example.forecast.data.repository.WeatherRepository
import com.example.forecast.data.dataclass.current.CurrentWeatherUI
import com.example.forecast.network.retrofit.RetrofitClient
import com.example.forecast.ui.mapper.CurrentWeatherUiMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class MainUIState {

    object Loading : MainUIState()
    data class Success(val cities: List<CurrentWeatherUI>) : MainUIState()
}

sealed class CitiesState {

    object Loading : CitiesState()
    object Standard : CitiesState()
}

sealed class RefreshCityState {

    object Standard : RefreshCityState()
    object Loading : RefreshCityState()
}

sealed class UiEventCities {

    object Offline : UiEventCities()
    object CityAlreadyAdded : UiEventCities()
    object ErrorAddingCity : UiEventCities()
    object ErrorRefresh : UiEventCities()
}

class MainViewModel : ViewModel() {

    private val currentUiMapper = CurrentWeatherUiMapper()

    private val _uiState = MutableLiveData<MainUIState>()
    val uiState: LiveData<MainUIState> get() = _uiState

    private val _citiesState = MutableLiveData<CitiesState>()
    val citiesState: LiveData<CitiesState> = _citiesState

    private val _refreshState = MutableLiveData<RefreshCityState>()
    val refreshState: LiveData<RefreshCityState> = _refreshState

    private val _events = MutableLiveData<Event<UiEventCities>>()
    val events: LiveData<Event<UiEventCities>> = _events

    private var isLoadedStartData = false
    private var loadCitiesJob: Job? = null
    private var addCitiesJob: Job? = null
    private var refreshSwipeJob: Job? = null
    private var wasLoadingWhenPaused = false
    private var restartRequiredLoadCities = false
    private var restartRequiredRefreshSwipe = false

    private var activeAddCities = 0
    private val mutex = Mutex()

    private fun isCachedValidCurrent(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.Cache.CACHE_VALIDITY_DURATION_MS
    }

    fun initData() {
        if (isLoadedStartData) return

        isLoadedStartData = true
        loadCities()
    }

    private fun loadCities() {
        if (loadCitiesJob?.isActive == true) return

        loadCitiesJob = viewModelScope.launch {
            _uiState.postValue(MainUIState.Loading)
            loadCitiesData()
        }
    }

    private suspend fun loadCitiesData() {
        WeatherRepository.initCacheFromDb()
        val cachedCities = WeatherRepository.getMemoryCities()
        val cities = mutableListOf<CurrentWeatherUI>()

        for (city in cachedCities) {
            val cached = WeatherRepository.getCachedDetails(city)

            val cacheCurrent = try {
                if (cached?.current != null && isCachedValidCurrent(cached.current.timestamp)) {
                    cached.current
                } else {
                    val weatherDto = RetrofitClient.weatherApi.getCurrentWeather(city)
                    WeatherRepository.setCachedCurrent(city, weatherDto)
                    WeatherRepository.getCachedDetails(city)?.current
                }
            } catch (e: Exception) {
                _events.value = Event(UiEventCities.Offline)
                loadCitiesJob?.cancel()
                loadCitiesJob = null
                cached?.current
            }

            cacheCurrent?.let { cities += currentUiMapper.map(it) }
        }

        _uiState.postValue(MainUIState.Success(cities))
    }

    private fun addNewCity(cityName: String) {
        addCitiesJob = viewModelScope.launch {
            try {
                activeAddCities++
                addCity(cityName)
            } finally {
                activeAddCities--
                _citiesState.postValue(
                    if (activeAddCities > 0) CitiesState.Loading else CitiesState.Standard
                )
            }
        }
    }

    private suspend fun addCity(cityName: String) = mutex.withLock {
        _citiesState.value = CitiesState.Loading

        val currentCities = WeatherRepository.getMemoryCities()

        if (currentCities.any { it.equals(cityName, ignoreCase = true) }) {
            _events.value = Event(UiEventCities.CityAlreadyAdded)
            _citiesState.postValue(CitiesState.Standard)
            return@withLock
        }

        try {
            val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(cityName, weather)

            val cities = WeatherRepository.getMemoryCities()
                .mapNotNull { WeatherRepository.getCachedDetails(it)?.current }
                .map(currentUiMapper::map)

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            _events.value = Event(UiEventCities.ErrorAddingCity)

            val cities = WeatherRepository.getMemoryCities()
                .mapNotNull { WeatherRepository.getCachedDetails(it)?.current }
                .map(currentUiMapper::map)

            _uiState.postValue(MainUIState.Success(cities))
            addCitiesJob?.cancel()
            addCitiesJob = null
        }
    }

    private suspend fun onSwipeRemoveCity(cityName: String) {
        WeatherRepository.removeCity(cityName)

        viewModelScope.launch {
            removeCitySwipe()
        }
    }

    private suspend fun removeCitySwipe() {
        val cachedCities = WeatherRepository.getMemoryCities()

        val cities = cachedCities.mapNotNull { city ->
            val cached = WeatherRepository.getCachedDetails(city)?.current
            if (cached != null && isCachedValidCurrent(cached.timestamp)) {
                currentUiMapper.map(cached)
            } else {
                null
            }
        }
        _uiState.postValue(MainUIState.Success(cities))
    }

    private fun onSwipeRefreshCities() {
        if (refreshSwipeJob?.isActive == true) return

        _refreshState.value = RefreshCityState.Loading

        refreshSwipeJob = viewModelScope.launch {
            try {
                refreshCitiesSwipe()
            } finally {
                _refreshState.value = RefreshCityState.Standard
            }
        }
    }

    private suspend fun refreshCitiesSwipe() {
        val cachedCities = WeatherRepository.getMemoryCities()
        val cities = mutableListOf<CurrentWeatherUI>()

        if (cachedCities.isEmpty()) {
            _uiState.postValue(MainUIState.Success(emptyList()))
            return
        }

        try {
            for (city in cachedCities) {
                val currentDto = RetrofitClient.weatherApi.getCurrentWeather(city)
                WeatherRepository.setCachedCurrent(city, currentDto)

                WeatherRepository.getCachedDetails(city)?.current?.let {
                    cities += currentUiMapper.map(it)
                }
            }
            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            _events.value = Event(UiEventCities.ErrorRefresh)
        }
    }

    private fun cancelLoadingOnPause() {
        wasLoadingWhenPaused = true

        if (loadCitiesJob?.isActive == true) {
            loadCitiesJob?.cancel()
            loadCitiesJob = null
            restartRequiredLoadCities = true
        }

        if (refreshSwipeJob?.isActive == true) {
            refreshSwipeJob?.cancel()
            refreshSwipeJob = null
            restartRequiredRefreshSwipe = true
        }
    }

    private fun retryInterruptedLoad() {
        if (wasLoadingWhenPaused) {

            if (restartRequiredLoadCities) {
                loadCities()
                restartRequiredLoadCities = false
            }

            if (restartRequiredRefreshSwipe) {
                onSwipeRefreshCities()
                restartRequiredRefreshSwipe = false
            }
        }
    }

    fun onStopFragment(isChangingConfigurations: Boolean) {
        if ((loadCitiesJob?.isActive == true || refreshSwipeJob?.isActive == true) &&
            !isChangingConfigurations) cancelLoadingOnPause()
    }

    fun onResumeFragment() {
        if (wasLoadingWhenPaused) retryInterruptedLoad()
    }

    fun onAddNewCity(cityName: String) {
        addNewCity(cityName)
    }

    suspend fun onSwipeRemove(cityName: String) {
        onSwipeRemoveCity(cityName)
    }

    fun onSwipeRefresh() {
        onSwipeRefreshCities()
    }
}