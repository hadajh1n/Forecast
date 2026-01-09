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
}

sealed class CitiesState {

    object Loading : CitiesState()
    object Standard : CitiesState()
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

    private val _messageEvent = MutableLiveData<Event<String>>()
    val messageEvent: LiveData<Event<String>> get() = _messageEvent

    private var isLoadedStartData = false

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
            Log.e("MainViewModel", "| ПРИЛОЖЕНИЕ ЗАПУЩЕНО, ПОЛУЧЕНИЕ ПЕРВИЧНЫХ ДАННЫХ |\nЗапуск корутины loadCitiesData")
            _uiState.postValue(MainUIState.Loading)
            loadCitiesData(context)
            Log.e("MainViewModel", "Корутина loadCitiesData завершена\n| ПОЛУЧЕНИЕ ПЕРВИЧНЫХ ДАННЫХ ЗАВЕРШЕНО |")
        }
    }

    private suspend fun loadCitiesData(context: Context) {
        WeatherRepository.initCacheFromDb()
        val cachedCities = WeatherRepository.getMemoryCities()
        val cities = mutableListOf<CurrentWeatherUI>()

        delay(4_000)

        for (city in cachedCities) {
            Log.e("MainViewModel", "Попытка загрузки для города $city")
            val cached = WeatherRepository.getCachedDetails(city)

            val cacheCurrent = try {
                if (cached?.current != null && isCachedValidCurrent(cached.current.timestamp)) {
                    Log.e("MainViewModel", "Кэш валиден для $city — используем его")
                    cached.current
                } else {
                    Log.e("MainViewModel", "Запрос API для города $city")
                    val weatherDto = RetrofitClient.weatherApi.getCurrentWeather(city)
                    WeatherRepository.setCachedCurrent(city, weatherDto)
                    WeatherRepository.getCachedDetails(city)?.current
                        ?: throw Exception("Данные после API null")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Оффлайн или ошибка API для $city: ${e.message}")
                _messageEvent.postValue(Event(context.getString(R.string.offline_mode)))
                Log.e("MainViewModel", "Отмена корутины")
                loadCitiesJob?.cancel()
                loadCitiesJob = null
                cached?.current
            }

            cacheCurrent?.let {
                cities += currentUiMapper.map(it)
            }
        }

        _uiState.postValue(MainUIState.Success(cities))
    }

    private fun addNewCity(cityName: String, context: Context) {
        addCitiesJob = viewModelScope.launch {
            try {
                Log.e("MainViewModel", "| ДОБАВЛЕНИЕ НОВОГО ГОРОДА : $cityName |")
                Log.e("MainViewModel", "+1 город к загрузке: $cityName")
                activeAddCities++
                Log.e("MainViewModel", "Запуск корутины addCity")
                addCity(cityName, context)
                Log.e("MainViewModel", "Корутина addCity завершена\n| ДОБАВЛЕНИЕ НОВОГО ГОРОДА ЗАВЕРШЕНО |")
            } finally {
                Log.e("MainViewModel", "-1 город к загрузке: $cityName")
                activeAddCities--
                _citiesState.postValue(
                    if (activeAddCities > 0) CitiesState.Loading else CitiesState.Standard
                )
                Log.e("MainViewModel", "Количество городов к загрузке: $activeAddCities")
            }
        }
    }

    private suspend fun addCity(cityName: String, context: Context) = mutex.withLock {
        _citiesState.value = CitiesState.Loading

        val currentCities = WeatherRepository.getMemoryCities()

        if (currentCities.any { it.equals(cityName, ignoreCase = true) }) {
            _messageEvent.postValue(Event(context.getString(R.string.error_city_already_added)))
            _citiesState.postValue(CitiesState.Standard)
            return@withLock
        }

        try {
            delay(7_000)

            Log.e("MainViewModel", "Запрос API для нового города")
            val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName)
            WeatherRepository.setCachedCurrent(cityName, weather)

            val cities = WeatherRepository.getMemoryCities()
                .mapNotNull { WeatherRepository.getCachedDetails(it)?.current }
                .map(currentUiMapper::map)

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Ошибка запроса API для нового города")
            _messageEvent.postValue(Event(context.getString(R.string.error_city_add)))

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
            Log.e("MainViewModel", "| УДАЛЕНИЕ ГОРОДА |\nЗапуск корутины removeCity")
            removeCitySwipe()
            Log.e("MainViewModel", "Корутина removeCity завершена\n| УДАЛЕНИЕ ГОРОДА ЗАВЕРШЕНО |")
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

    private fun onSwipeRefreshCities(context: Context) {
        if (refreshSwipeJob?.isActive == true) {
            Log.e("MainViewModel", "Refresh уже начался...")
            return
        }

        _refreshState.value = RefreshCityState.Loading

        refreshSwipeJob = viewModelScope.launch {
            try {
                Log.e("MainViewModel", "| REFRESH SWIPE |\nЗапуск корутины refreshSwipe")
                refreshCitiesSwipe(context)
                Log.e("MainViewModel", "Корутина refreshSwipe завершена\n| REFRESH SWIPE ЗАВЕРШЕН |")
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
                val currentDto = RetrofitClient.weatherApi.getCurrentWeather(city)
                WeatherRepository.setCachedCurrent(city, currentDto)

                WeatherRepository.getCachedDetails(city)?.current?.let {
                    cities += currentUiMapper.map(it)
                }
            }

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Ошибка refresh API")
            _messageEvent.postValue(Event(context.getString(R.string.error_pull_to_refresh)))
        }
    }

    private fun refreshBackground() {
        if (refreshBackgroundJob?.isActive == true) return

        refreshBackgroundJob = viewModelScope.launch {
            Log.e("MainViewModel", "Запуск корутины refreshBackground")
            startRefreshBackground()
            Log.e("MainViewModel", "Корутина refreshBackground завершена")
        }
    }

    private suspend fun startRefreshBackground() {
        val cachedCities = WeatherRepository.getMemoryCities()
        val cities = mutableListOf<CurrentWeatherUI>()

        if (cachedCities.isEmpty()) {
            _uiState.postValue(MainUIState.Success(emptyList()))
            return
        }

        try {
            for (city in cachedCities) {
                Log.e("MainViewModel", "Фоновый refresh - Запрос API для города $city")
                val currentDto = RetrofitClient.weatherApi.getCurrentWeather(city)
                WeatherRepository.setCachedCurrent(city, currentDto)

                WeatherRepository.getCachedDetails(city)?.current?.let {
                    cities += currentUiMapper.map(it)
                }
            }

            _uiState.postValue(MainUIState.Success(cities))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Ошибка фонового refresh для городов")
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

    suspend fun onSwipeRemove(cityName: String) {
        onSwipeRemoveCity(cityName)
    }

    fun onSwipeRefresh(context: Context) {
        onSwipeRefreshCities(context)
    }

    fun onRefreshBackground() {
//        refreshBackground()
    }
}