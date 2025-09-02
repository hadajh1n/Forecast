package com.example.forecast.viewModel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.R
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.launch

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
                val currentState = _uiState.value
                if (currentState is MainUIState.Success) {
                    val updatedCities = currentState.cities.toMutableList().apply { add(weather) }
                    _uiState.value = MainUIState.Success(updatedCities)
                } else {
                    _uiState.value = MainUIState.Success(listOf(weather))
                }
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
        prefs.edit().putStringSet(PREFS_KEY_CITIES, cityNames.toSet()).apply()
    }

    private fun getCitiesFromPrefs(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(PREFS_KEY_CITIES, emptySet())?.toList() ?: emptyList()
    }

    fun loadCitiesFromPrefs(context: Context) {
        viewModelScope.launch {
            _uiState.value = MainUIState.Loading
            try {
                val apiKey = getApiKey(context)
                val cityNames = getCitiesFromPrefs(context)
                if (cityNames.isEmpty()) {
                    _uiState.value = MainUIState.Success(emptyList())
                    return@launch
                }

                val cities = loadWeatherForCities(cityNames, apiKey)
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
        val currentState = _uiState.value
        if (currentState is MainUIState.Success) {
            val updatedCities = currentState.cities.filter {
                !it.name.equals(cityName, ignoreCase = true)
            }
            _uiState.value = MainUIState.Success(updatedCities)
        } else if (currentCities.isEmpty()) {
            _uiState.value = MainUIState.Success(emptyList())
        }
    }
}