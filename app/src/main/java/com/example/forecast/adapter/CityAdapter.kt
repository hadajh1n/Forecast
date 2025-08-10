package com.example.forecast.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.R
import com.example.forecast.activity.DetailActivity
import com.example.forecast.databinding.ItemCityBinding
import com.example.forecast.dataclass.CurrentWeather
import kotlin.math.roundToInt

class CityAdapter : RecyclerView.Adapter<CityAdapter.WeatherViewHolder>() {

    val cityList = ArrayList<CurrentWeather>()

    class WeatherViewHolder(item: View) : RecyclerView.ViewHolder(item) {
        private val binding = ItemCityBinding.bind(item)

        fun bind(currentWeather: CurrentWeather) = with(binding) {
            tvCity.text = currentWeather.name
            tvTemperature.text = "${currentWeather.main.temp.roundToInt()}°C"

            root.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, DetailActivity::class.java).apply {
                    putExtra("CITY_NAME", currentWeather.name)
                    putExtra("TEMPERATURE", currentWeather.main.temp)
                    putExtra("ICON", currentWeather.weather[0].icon)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_city, parent, false)
        return WeatherViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
        holder.bind(cityList[position])
    }

    override fun getItemCount(): Int = cityList.size
}