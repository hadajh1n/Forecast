package com.example.forecast.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.R
import com.example.forecast.databinding.ItemCityBinding
import com.example.forecast.dataclass.CurrentWeather
import kotlin.math.roundToInt

class CityAdapter(private val onItemClick: (CurrentWeather) -> Unit) : RecyclerView.Adapter<CityAdapter.WeatherViewHolder>() {

    val cityList = ArrayList<CurrentWeather>()

    inner class WeatherViewHolder(item: View) : RecyclerView.ViewHolder(item) {
        private val binding = ItemCityBinding.bind(item)

        fun bind(currentWeather: CurrentWeather) = with(binding) {
            tvCity.text = currentWeather.name
            tvTemperature.text = "${currentWeather.main.temp.roundToInt()}°C"

            root.setOnClickListener {
                onItemClick(currentWeather)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_city, parent, false)
        return WeatherViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
        holder.bind(cityList[position])
    }

    override fun getItemCount() = cityList.size
}
