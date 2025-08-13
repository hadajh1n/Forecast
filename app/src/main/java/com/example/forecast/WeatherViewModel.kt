package com.example.forecast.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.Constants
import com.example.forecast.R
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastItem
import com.example.forecast.dataclass.ForecastMain
import com.example.forecast.dataclass.ForecastUI
import com.example.forecast.dataclass.ForecastWeather
import com.example.forecast.dataclass.Weather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WeatherViewModel : ViewModel() {
    private val _cities = MutableLiveData<List<CurrentWeather>>(emptyList())
    val cities: LiveData<List<CurrentWeather>> get() = _cities

    private val _currentWeather = MutableLiveData<CurrentWeather?>()
    val currentWeather: LiveData<CurrentWeather?> get() = _currentWeather

    private val _forecast = MutableLiveData<List<ForecastUI>>()
    val forecast: LiveData<List<ForecastUI>> get() = _forecast

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val SECONDS_TO_MILLIS = 1000L

    // Функция добавления города
    fun addCity(cityName: String, context: Context) {
        viewModelScope.launch {
            try {
                val apiKey = context.getString(R.string.weather_api_key)
                if (apiKey.isEmpty()) {
                    _error.value = context.getString(R.string.error_api_key_missing)
                    return@launch
                }

                val currentCities = _cities.value.orEmpty().toMutableList()

                // Проверка на дубликат города (регистр игнонируется)
                if (currentCities.any { it.name.equals(cityName, ignoreCase = true) }) {
                    _error.value = context.getString(R.string.error_city_already_added)
                    return@launch
                }

                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                currentCities.add(weather)
                _cities.value = currentCities
                saveCities(context, currentCities.map { it.name })
            } catch (e: Exception) {
                _error.value = context.getString(R.string.error_city_not_found)
            }
        }
    }

    // Сохранение списка городов в SharedPreferences
    private fun saveCities(context: Context, cityNames: List<String>) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(Constants.PREFS_KEY_CITIES, cityNames.toSet()).apply()
    }

    // Возвращение списка городов из SharedPreferences
    private fun getCitiesFromPrefs(context: Context): List<String> {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(Constants.PREFS_KEY_CITIES, emptySet())?.toList() ?: emptyList()
    }

    // Загрузка списка городов из SharedPreferences
    fun loadCitiesFromPrefs(context: Context) {
        viewModelScope.launch {
            val apiKey = context.getString(R.string.weather_api_key)
            if (apiKey.isEmpty()) {
                _error.value = context.getString(R.string.error_api_key_missing)
                return@launch
            }
            val cityName = getCitiesFromPrefs(context)
            val currentCities = mutableListOf<CurrentWeather>()
            for (cityName in cityName) {
                try {
                    val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                    currentCities.add(weather)
                } catch (e: Exception) {
                    _error.value = context.getString(R.string.error_load_cities)
                }
            }
            _cities.value = currentCities
        }
    }

    // Удаление города свайпом
    fun removeCity(cityName: String, context: Context) {
        val currentCities = _cities.value.orEmpty().toMutableList()
        currentCities.removeAll { it.name.equals(cityName, ignoreCase = true) }
        _cities.value = currentCities
        saveCities(context, currentCities.map { it.name })
    }

    fun fetchCurrentWeather(cityName: String, context: Context) {
        viewModelScope.launch {
            try {
                val apiKey = context.getString(R.string.weather_api_key)
                if (apiKey.isEmpty()) {
                    _error.value = context.getString(R.string.error_api_key_missing)
                    return@launch
                }
                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                _currentWeather.value = weather
            } catch (e: Exception) {
                _error.value = context.getString(R.string.error_fetch_current_weather)
            }
        }
    }

    fun fetchForecast(cityName: String, context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val apiKey = context.getString(R.string.weather_api_key)
                val forecastResponse = RetrofitClient.weatherApi.getForecast(cityName, apiKey)
                val dailyForecasts = groupForecastByDay(forecastResponse)
                val nextDays = dailyForecasts.take(6)

                val uiList = nextDays.map { forecast ->
                    val date = Date(forecast.dt * SECONDS_TO_MILLIS)
                    val dayFormat = SimpleDateFormat("E", Locale("ru"))
                    val dayOfWeek = dayFormat.format(date).uppercase()
                    val iconUrl = Constants.WEATHER_ICON_URL.format(forecast.weather[0].icon)
                    ForecastUI(
                        dayOfWeek = dayOfWeek,
                        iconUrl = iconUrl,
                        tempMax = context.getString(R.string.temperature_format, forecast.main.tempMax.toInt()),
                        tempMin = context.getString(R.string.temperature_format, forecast.main.tempMin.toInt())
                    )
                }

                _forecast.value = uiList
            } catch (e: Exception) {
                _error.value = context.getString(R.string.error_fetch_forecast)
            }
        }
    }

    private fun groupForecastByDay(response: ForecastWeather): List<ForecastItem> {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.add(Calendar.DAY_OF_YEAR, 1) // Начало со следующего дня
        val nextDay = calendar.get(Calendar.DAY_OF_YEAR)

        val dailyForecasts = mutableListOf<ForecastItem>()
        val groupedByDay = response.list.groupBy { item ->
            calendar.time = Date(item.dt * SECONDS_TO_MILLIS)
            calendar.get(Calendar.DAY_OF_YEAR)
        }

        // Собираем данные для следующих 6 дней
        for (day in nextDay until nextDay + 6) {
            val dayData = groupedByDay[day]
            if (dayData != null && dayData.isNotEmpty()) {
                // Вычисление максимальной и минимальной температуры за день
                val maxTemp = dayData.maxOfOrNull { it.main.temp } ?: 0f
                val minTemp = dayData.minOfOrNull { it.main.temp } ?: 0f
                val icon = dayData.first().weather[0].icon // Иконка первого прогноза
                val dt = dayData.first().dt // Временная метка первого прогноза
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