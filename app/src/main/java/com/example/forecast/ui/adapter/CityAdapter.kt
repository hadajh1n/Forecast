package com.example.forecast.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.R
import com.example.forecast.databinding.ItemCityBinding
import com.example.forecast.data.dataclass.CurrentWeather
import kotlin.math.round

class CityAdapter(
    private val onItemClick: (CurrentWeather) -> Unit
) : RecyclerView.Adapter<CityAdapter.WeatherViewHolder>() {

    val cityList = mutableListOf<CurrentWeather>()

    fun updateCities(newCities: List<CurrentWeather>) {
        val diffCallback = CityDiffCallback(cityList, newCities)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        cityList.clear()
        cityList.addAll(newCities)
        diffResult.dispatchUpdatesTo(this@CityAdapter)
    }

    fun removeCity(position: Int): CurrentWeather {
        val city = cityList[position]
        cityList.removeAt(position)
        notifyItemRemoved(position)
        return city
    }

    inner class WeatherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = ItemCityBinding.bind(view)

        fun bind(currentWeather: CurrentWeather, context: Context) = with(binding) {
            tvCity.text = currentWeather.name
            tvTemperature.text = context.getString(
                R.string.temperature_format,
                round(currentWeather.main.temp).toInt(),
            )

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
        holder.bind(cityList[position], holder.itemView.context)
    }

    override fun getItemCount(): Int = cityList.size
}

class CityDiffCallback(
    private val oldList: List<CurrentWeather>,
    private val newList: List<CurrentWeather>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].name == newList[newItemPosition].name
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}