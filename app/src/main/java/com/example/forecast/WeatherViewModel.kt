package com.example.forecast.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {
    private val _cities = MutableLiveData<List<CurrentWeather>>(emptyList())
    val cities : LiveData<List<CurrentWeather>> get() = _cities

    private val _error = MutableLiveData<String>()
    val error : LiveData<String> get() = _error

    private val apiKey = "3a40caaed30624dd3ed13790e371b4bd"

    fun addCity(cityName : String) {
        viewModelScope.launch {
            try {
                val weather = RetrofitClient.weatherApi.getCurrentWeather(cityName, apiKey)
                val currentCities = _cities.value.orEmpty().toMutableList()
                currentCities.add(weather)
                _cities.value = currentCities
            } catch (e: Exception) {
                _error.value = "Город не найден или проблемы с сетью"
            }
        }
    }
}