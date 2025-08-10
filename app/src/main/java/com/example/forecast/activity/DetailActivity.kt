package com.example.forecast.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.adapter.DetailAdapter
import com.example.forecast.databinding.ActivityDetailBinding
import com.example.forecast.dataclass.ForecastItem
import com.example.forecast.dataclass.ForecastMain
import com.example.forecast.dataclass.ForecastWeather
import com.example.forecast.dataclass.Weather
import com.example.forecast.retrofit.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private val detailAdapter = DetailAdapter()
    private val apiKey = "3a40caaed30624dd3ed13790e371b4bd"

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
                // Группировка данных по дням
                val dailyForecasts = groupForecastByDay(forecastResponse)
                // Отображание только следующих 6 дней
                val nextDays = dailyForecasts.take(6)
                detailAdapter.detailList.clear()
                detailAdapter.detailList.addAll(nextDays)
                detailAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                // Игнорирование ошибки
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
            calendar.time = Date(item.dt * 1000)
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