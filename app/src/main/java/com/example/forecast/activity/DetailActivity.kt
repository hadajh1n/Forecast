package com.example.forecast.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.adapter.DetailAdapter
import com.example.forecast.databinding.ActivityDetailBinding
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

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private val detailAdapter = DetailAdapter()
    private val apiKey = "3a40caaed30624dd3ed13790e371b4bd"
    private val SECONDS_TO_MILLIS = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка RecyclerView для прогноза
        binding.rvWeather.adapter = detailAdapter
        binding.rvWeather.layoutManager = LinearLayoutManager(this)

        // Получаем данные из Intent
        val cityName = intent.getStringExtra("CITY_NAME") ?: "Неизвестный город"
        val temperature = intent.getFloatExtra("TEMPERATURE", 0f)
        val icon = intent.getStringExtra("ICON") ?: ""

        // Отображение текущих данных
        binding.tvCity.text = cityName
        binding.tvTemperature.text = "${temperature.toInt()}°C"
        val iconUrl = "https://openweathermap.org/img/wn/$icon.png"
        Glide.with(this)
            .load(iconUrl)
            .into(binding.imWeather)

        // Запрос прогноза
        fetchForecast(cityName)
    }

    private fun fetchForecast(cityName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val forecastResponse = RetrofitClient.weatherApi.getForecast(cityName, apiKey)
                val dailyForecasts = groupForecastByDay(forecastResponse)
                val nextDays = dailyForecasts.take(6)

                val uiList = nextDays.map { forecast ->
                    val date = Date(forecast.dt * 1000)
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

                detailAdapter.detailList.clear()
                detailAdapter.detailList.addAll(uiList)
                detailAdapter.notifyDataSetChanged()
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