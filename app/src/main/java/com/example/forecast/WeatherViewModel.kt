package com.example.forecast.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val apiKey = "3a40caaed30624dd3ed13790e371b4bd"
    private val SECONDS_TO_MILLIS = 1000L

    // Функция добавления города
    fun addCity(cityName: String, context: Context) {
        viewModelScope.launch {
            try {
                val currentCities = _cities.value.orEmpty().toMutableList()

                // Проверка на дубликат города (регистр игнонируется)
                if (currentCities.any { it.name.equals(cityName, ignoreCase = true) }) {
                    _error.value = "Город уже добавлен"
                    return@launch
                }

                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                currentCities.add(weather)
                _cities.value = currentCities
                saveCities(context, currentCities.map { it.name })
            } catch (e: Exception) {
                _error.value = "Город не найден или проблемы с сетью"
            }
        }
    }

    // Сохранение списка городов в SharedPreferences
    private fun saveCities(context: Context, cityNames: List<String>) {
        val prefs = context.getSharedPreferences("WeatherAppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("cities", cityNames.toSet()).apply()
    }

    // Возвращение списка городов из SharedPreferences
    private fun getCitiesFromPrefs(context: Context): List<String> {
        val prefs = context.getSharedPreferences("WeatherAppPrefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("cities", emptySet())?.toList() ?: emptyList()
    }

    // Загрузка списка городов из SharedPreferences
    fun loadCitiesFromPrefs(context: Context) {
        viewModelScope.launch {
            val cityName = getCitiesFromPrefs(context)
            val currentCities = mutableListOf<CurrentWeather>()
            for (cityName in cityName) {
                try {
                    val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                    currentCities.add(weather)
                } catch (e: Exception) {

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

    fun fetchCurrentWeather(cityName: String) {
        viewModelScope.launch {
            try {
                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                _currentWeather.value = weather
            } catch (e: Exception) {

            }
        }
    }

    fun fetchForecast(cityName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val forecastResponse = RetrofitClient.weatherApi.getForecast(cityName, apiKey)
                val dailyForecasts = groupForecastByDay(forecastResponse)
                val nextDays = dailyForecasts.take(6)

                val uiList = nextDays.map { forecast ->
                    val date = Date(forecast.dt * SECONDS_TO_MILLIS)
                    val dayFormat = SimpleDateFormat("E", Locale("ru"))
                    val dayOfWeek = dayFormat.format(date).uppercase()
                    val iconUrl = "https://openweathermap.org/img/wn/${forecast.weather[0].icon}.png"
                    ForecastUI(
                        dayOfWeek = dayOfWeek,
                        iconUrl = iconUrl,
                        tempMax = "${forecast.main.tempMax.toInt()}°C",
                        tempMin = "${forecast.main.tempMin.toInt()}°C"
                    )
                }

                _forecast.value = uiList
            } catch (e: Exception) {

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