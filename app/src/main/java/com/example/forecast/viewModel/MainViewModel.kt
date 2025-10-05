package com.example.forecast.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.Constants
import com.example.forecast.R
import com.example.forecast.WeatherRepository
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

sealed class MainUIState {
    object Loading : MainUIState()
    data class Success(val cities: List<CurrentWeather>) : MainUIState()
    data class Error(val message: String) : MainUIState()
}

class MainViewModel : ViewModel() {

    companion object {
        private const val PREFS_NAME = "WeatherAppPrefs"
        private const val PREFS_KEY_CITIES = "cities"
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableLiveData<MainUIState>()
    val uiState: LiveData<MainUIState> get() = _uiState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage : LiveData<String> get() = _errorMessage

    private var refreshJob: Job? = null

    private fun getApiKey(context: Context) : String {
        val apiKey = context.getString(R.string.weather_api_key)
        if (apiKey.isEmpty()) {
            throw IllegalStateException("Missing API key")
        }

        return apiKey
    }

    fun addCity(cityName: String, context: Context) {
        viewModelScope.launch {
            try {
                val apiKey = getApiKey(context)
                val currentCities = getCitiesFromPrefs(context).toMutableList()
                if (currentCities.any { it.equals(cityName, ignoreCase = true) }) {
                    _errorMessage.value = context.getString(R.string.error_city_already_added)
                    return@launch
                }

                currentCities.add(cityName)
                saveCities(context, currentCities)

                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                WeatherRepository.setCachedCurrentWeather(
                    cityName,
                    weather,
                    System.currentTimeMillis()
                )

                val cityNames = getCitiesFromPrefs(context)
                val cities = cityNames.mapNotNull { name ->
                    WeatherRepository.getCachedDetails(name)?.weather
                }

                _uiState.value = MainUIState.Success(cities)
            } catch (e: Exception) {
                val cityNames = getCitiesFromPrefs(context)
                val cachedCities =
                    cityNames.mapNotNull { WeatherRepository.getCachedDetails(it)?.weather }
                if (cachedCities.isNotEmpty()) {
                    _uiState.value = MainUIState.Success(cachedCities)
                } else {
                    _uiState.value =
                        MainUIState.Error(context.getString(R.string.error_load_cities))
                }
            }
        }
    }

    private suspend fun fetchCurrentWeatherForCity(
        cityName: String,
        apiKey: String
    ): CurrentWeather {
        return RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
    }

    private fun saveCities(context: Context, cityNames: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONArray(cityNames).toString()
        prefs.edit().putString(PREFS_KEY_CITIES, json).apply()
    }

    private fun getCitiesFromPrefs(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY_CITIES, null) ?: return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    private suspend fun loadCitiesData(context: Context, showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.value = MainUIState.Loading
        }

        try {
            val apiKey = getApiKey(context)
            val cityNames = getCitiesFromPrefs(context)
            val cities = mutableListOf<CurrentWeather>()

            for (name in cityNames) {
                val cached = WeatherRepository.getCachedDetails(name)
                if (cached != null && isCachedValid(cached.timestamp)) {
                    cities.add(cached.weather)
                } else {
                    val weather = fetchCurrentWeatherForCity(name, apiKey)
                    WeatherRepository.setCachedCurrentWeather(
                        name,
                        weather,
                        System.currentTimeMillis()
                    )
                    cities.add(weather)
                }
            }

            _uiState.value = MainUIState.Success(cities)
        } catch (e: Exception) {
            val cityNames = getCitiesFromPrefs(context)
            val cachedCities =
                cityNames.mapNotNull { WeatherRepository.getCachedDetails(it)?.weather }
            if (cachedCities.isNotEmpty()) {
                _uiState.value = MainUIState.Success(cachedCities)
            } else {
                _uiState.value = MainUIState.Error(context.getString(R.string.error_load_cities))
            }
        }
    }

    fun loadCitiesFromPrefs(context: Context) {
        viewModelScope.launch {
            loadCitiesData(context, showLoading = true)
        }
    }

    private fun isCachedValid(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) <
                Constants.CacheLifetime.CACHE_VALIDITY_DURATION
    }

    fun removeCity(cityName: String, context: Context) {
        val currentCities = getCitiesFromPrefs(context).toMutableList()
        currentCities.removeAll { it.equals(cityName, ignoreCase = true) }
        saveCities(context, currentCities)
        WeatherRepository.removeCity(cityName)

        viewModelScope.launch {
            loadCitiesData(context, showLoading = false)
        }
    }

    fun startRefresh(context: Context) {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            while (isActive) {
                val cityNames = getCitiesFromPrefs(context)
                var minDelay = Constants.CacheLifetime.CACHE_VALIDITY_DURATION
                var dataChanged = false

                for (name in cityNames) {
                    val timestamp = WeatherRepository.getCityTimestamp(name) ?: 0
                    val age = System.currentTimeMillis() - timestamp
                    if (age >= Constants.CacheLifetime.CACHE_VALIDITY_DURATION) {
                        try {
                            val apiKey = getApiKey(context)
                            val weather = fetchCurrentWeatherForCity(name, apiKey)
                            WeatherRepository.setCachedCurrentWeather(
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
                    if (remaining < minDelay) minDelay = remaining.coerceAtLeast(1000)
                }

                if (dataChanged) {
                    val cities =
                        cityNames.mapNotNull { WeatherRepository.getCachedDetails(it)?.weather }
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
        stopRefresh()
    }
}