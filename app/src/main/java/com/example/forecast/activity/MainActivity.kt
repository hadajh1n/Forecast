package com.example.forecast.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
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

        binding.rvCity.adapter = cityAdapter
        binding.rvCity.layoutManager = LinearLayoutManager(this@MainActivity)

        viewModel.cities.observe(this@MainActivity, Observer { cities ->
            cityAdapter.cityList.clear()
            cityAdapter.cityList.addAll(cities)
            cityAdapter.notifyDataSetChanged()
        })

        viewModel.error.observe(this@MainActivity, Observer { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        })

        binding.btnAddCity.setOnClickListener {
            showAddCityDialog()
        }
    }

    private fun showAddCityDialog() {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Добавить город")

        val input = android.widget.EditText(this@MainActivity)
        input.hint = "Введите название города"
        builder.setView(input)

        builder.setPositiveButton("Добавить") { _, _ ->
            val cityName = input.text.toString().trim()
            if (cityName.isNotEmpty()) {
                viewModel.addCity(cityName)
            } else {
                Toast.makeText(this@MainActivity, "Введите название города", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }

        builder.show()
    }
}