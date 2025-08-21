package com.example.forecast.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.R
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastItem
import com.example.forecast.dataclass.ForecastMain
import com.example.forecast.dataclass.ForecastUI
import com.example.forecast.dataclass.ForecastWeather
import com.example.forecast.dataclass.Weather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class MainUIState {
    object Loading : MainUIState()
    data class Success(val cities: List<CurrentWeather>) : MainUIState()
    data class Error(val message: String) : MainUIState()
}

sealed class DetailUIState {
    object Loading : DetailUIState()
    data class Success(
        val temperature: String,
        val iconUrl: String,
        val forecast: List<ForecastUI>
    ) : DetailUIState()
    data class Error(val message: String) : DetailUIState()
}

class WeatherViewModel : ViewModel() {
    private val _uiState = MutableLiveData<MainUIState>()
    val uiState: LiveData<MainUIState> get() = _uiState

    private val _detailState = MutableLiveData<DetailUIState>()
    val detailState: LiveData<DetailUIState> get() = _detailState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage : LiveData<String> get() = _errorMessage

    private val SECONDS_TO_MILLIS = 1000L
    private val PREFS_NAME = "WeatherAppPrefs"
    private val PREFS_KEY_CITIES = "cities"
    private val WEATHER_ICON_URL = "https://openweathermap.org/img/wn/%s.png"

    private fun getApiKey(context: Context) : String {
        val apiKey = context.getString(R.string.weather_api_key)
        if (apiKey.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.error_api_key_missing))
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

    private suspend fun loadWeatherForCities(cityNames: List<String>, apiKey: String): List<CurrentWeather> {
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
            val updatedCities = currentState.cities.filter { !it.name.equals(cityName, ignoreCase = true) }
            _uiState.value = MainUIState.Success(updatedCities)
        } else if (currentCities.isEmpty()) {
            _uiState.value = MainUIState.Success(emptyList())
        }
    }

    fun loadCityDetail(cityName: String, context: Context) {
        viewModelScope.launch {
            _detailState.value = DetailUIState.Loading
            try {
                val apiKey = getApiKey(context)
                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                val forecastResponse = RetrofitClient.weatherApi.getForecast(cityName, apiKey)

                val dailyForecasts = groupForecastByDay(forecastResponse)
                val uiForecast = dailyForecasts.take(6).map {
                    ForecastUI(
                        dayOfWeek = SimpleDateFormat("E", Locale("ru")).format(Date(it.dt * SECONDS_TO_MILLIS)).uppercase(),
                        iconUrl = WEATHER_ICON_URL.format(it.weather[0].icon),
                        tempMax = context.getString(R.string.temperature_format, it.main.tempMax.toInt()),
                        tempMin = context.getString(R.string.temperature_format, it.main.tempMin.toInt())
                    )
                }

                _detailState.value = DetailUIState.Success(
                    temperature = context.getString(R.string.temperature_format, weather.main.temp.toInt()),
                    iconUrl = WEATHER_ICON_URL.format(weather.weather[0].icon),
                    forecast = uiForecast
                )
            } catch (e: Exception) {
                _detailState.value = DetailUIState.Error(context.getString(R.string.error_fetch_current_weather))
            }
        }
    }

    private fun groupForecastByDay(response: ForecastWeather): List<ForecastItem> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val nextDay = calendar.get(Calendar.DAY_OF_YEAR)

        val dailyForecasts = mutableListOf<ForecastItem>()
        val groupedByDay = response.list.groupBy { item ->
            val itemCal = Calendar.getInstance()
            itemCal.time = Date(item.dt * SECONDS_TO_MILLIS)
            itemCal.get(Calendar.DAY_OF_YEAR)
        }

        for (day in nextDay until nextDay + 6) {
            val dayData = groupedByDay[day]
            if (dayData != null && dayData.isNotEmpty()) {
                val maxTemp = dayData.maxOfOrNull { it.main.temp } ?: 0f
                val minTemp = dayData.minOfOrNull { it.main.temp } ?: 0f
                val icon = dayData.first().weather[0].icon
                val dt = dayData.first().dt
                dailyForecasts.add(
                    ForecastItem(
                        dt = dt,
                        main = ForecastMain(temp = 0f, tempMax = maxTemp, tempMin = minTemp),
                        weather = listOf(Weather(icon = icon))
                    )
                )
            }
        }
        return dailyForecasts
    }
}