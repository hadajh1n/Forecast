package com.example.forecast.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {
    private val _cities = MutableLiveData<List<CurrentWeather>>(emptyList())
    val cities: LiveData<List<CurrentWeather>> get() = _cities

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val apiKey = "3a40caaed30624dd3ed13790e371b4bd"

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
}