package com.example.forecast.viewModel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.Constants
import com.example.forecast.R
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
    }

    private val _uiState = MutableLiveData<MainUIState>()

    val uiState: LiveData<MainUIState> get() = _uiState

    private val _errorMessage = MutableLiveData<String>()

    val errorMessage : LiveData<String> get() = _errorMessage

    private var cachedCities: List<CurrentWeather>? = null
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
                val updatedCities = (cachedCities ?: emptyList()).toMutableList()
                updatedCities.add(weather)
                cachedCities = updatedCities
                _uiState.value = MainUIState.Success(updatedCities)
            } catch (e: Exception) {
                _uiState.value = MainUIState.Error(context.getString(R.string.error_load_cities))
            }
        }
    }

    private suspend fun loadWeatherForCities(
        cityNames: List<String>,
        apiKey: String
    ): List<CurrentWeather> {
        val currentCities = mutableListOf<CurrentWeather>()

        for (name in cityNames) {
            val weather = RetrofitClient.weatherApi.getCurrentWeather(name, apiKey)
            currentCities.add(weather)
        }

        return currentCities
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

    fun loadCitiesFromPrefs(context: Context) {
        viewModelScope.launch {
            if (cachedCities != null) {
                _uiState.value = MainUIState.Success(cachedCities!!)
                return@launch
            }

            _uiState.value = MainUIState.Loading
            try {
                val apiKey = getApiKey(context)
                val cityNames = getCitiesFromPrefs(context)
                if (cityNames.isEmpty()) {
                    _uiState.value = MainUIState.Success(emptyList())
                    return@launch
                }

                val cities = loadWeatherForCities(cityNames, apiKey)
                cachedCities = cities
                _uiState.value = MainUIState.Success(cities)
            } catch (e: Exception) {
                _uiState.value = MainUIState.Error(context.getString(R.string.error_load_cities))
            }
        }
    }

    fun removeCity(cityName: String, context: Context) {
        val currentCities = getCitiesFromPrefs(context).toMutableList()
        currentCities.removeAll { it.equals(cityName, ignoreCase = true) }
        saveCities(context, currentCities)

        val updatedCities = (cachedCities ?: emptyList()).filter {
            !it.name.equals(cityName, ignoreCase = true)
        }
        cachedCities = updatedCities
        _uiState.value = MainUIState.Success(updatedCities)
    }

    fun startRefresh(context: Context, interval: Long = Constants.Weather.REFRESH_INTERVAL_MILLIS) {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshWeather(context)
                delay(interval)
            }
        }
    }

    private suspend fun refreshWeather(context: Context) {
        try {
            val apiKey = getApiKey(context)
            val cityNames = getCitiesFromPrefs(context)
            if (cityNames.isEmpty()) {
                _uiState.value = MainUIState.Success(emptyList())
                return
            }

            val cities = loadWeatherForCities(cityNames, apiKey)
            cachedCities = cities
            _uiState.value = MainUIState.Success(cities)
        } catch (e: Exception) {
            _uiState.value = MainUIState.Error(context.getString(R.string.error_load_cities))
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