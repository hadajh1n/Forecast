package com.example.forecast.activity

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.forecast.R
import com.example.forecast.adapter.CityAdapter
import com.example.forecast.databinding.ActivityMainBinding
import com.example.forecast.viewmodel.WeatherViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: WeatherViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private val cityAdapter = CityAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация RecycleView
        binding.rvCity.adapter = cityAdapter
        binding.rvCity.layoutManager = LinearLayoutManager(this@MainActivity)

        // Подписка на список городов (WeatherViewModel)
        viewModel.cities.observe(this@MainActivity, Observer { cities ->
            cityAdapter.cityList.clear()
            cityAdapter.cityList.addAll(cities)
            cityAdapter.notifyDataSetChanged()
        })

        // Подписка на ошибки при поиске города (WeatherViewModel)
        viewModel.error.observe(this@MainActivity, Observer { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        })

        // Загрузка сохраненных городов при запуске
        viewModel.loadCitiesFromPrefs(this@MainActivity)

        // Кнопка для добавления города
        binding.btnAddCity.setOnClickListener {
            showAddCityDialog()
        }
    }

    // Реализация диалогового окна при добавлении города
    private fun showAddCityDialog() {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Добавить город")

        val input = AutoCompleteTextView(this@MainActivity)
        input.hint = "Введите название города"
        input.isSingleLine = true   // Ограничение одной строкой
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES // Начало с заглавной буквы

        val cities = resources.getStringArray(R.array.cities) // Заготовленный список городов

        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, cities)
        input.setAdapter(adapter)
        input.threshold = 1

        builder.setView(input)

        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }

        val dialog = builder.show()

        input.setOnItemClickListener { _, _, position, _ ->
            val cityName = adapter.getItem(position).toString().trim()
            if (cityName.isNotEmpty()) {
                viewModel.addCity(cityName, this@MainActivity)
            } else {
                Toast.makeText(this@MainActivity, "Город не выбран", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
    }
}