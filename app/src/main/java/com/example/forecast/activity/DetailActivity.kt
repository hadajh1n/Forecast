package com.example.forecast.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.adapter.DetailAdapter
import com.example.forecast.databinding.ActivityDetailBinding
import com.example.forecast.viewmodel.WeatherViewModel

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private val viewModel: WeatherViewModel by viewModels()
    private val detailAdapter = DetailAdapter()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка RecyclerView для прогноза
        binding.rvWeather.adapter = detailAdapter
        binding.rvWeather.layoutManager = LinearLayoutManager(this)

        viewModel.forecast.observe(this@DetailActivity, Observer { weather ->
            detailAdapter.detailList.clear()
            detailAdapter.detailList.addAll(weather)
            detailAdapter.notifyDataSetChanged()
        })

        viewModel.error.observe(this@DetailActivity, Observer { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this@DetailActivity, message, Toast.LENGTH_SHORT).show()
            }
        })

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

        viewModel.fetchForecast(cityName)
    }
}