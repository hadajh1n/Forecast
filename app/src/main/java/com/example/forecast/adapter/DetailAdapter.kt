package com.example.forecast.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.forecast.R
import com.example.forecast.databinding.ItemDetailBinding
import com.example.forecast.dataclass.CurrentWeather
import com.example.forecast.dataclass.ForecastItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailAdapter: RecyclerView.Adapter<DetailAdapter.DetailViewHolder>() {

    val detailList = ArrayList<ForecastItem>()

    class DetailViewHolder(item : View): RecyclerView.ViewHolder(item) {
        private val binding = ItemDetailBinding.bind(item)

        fun bind(forecast: ForecastItem) = with(binding) {
            val date = Date(forecast.dt * 1000)
            val dayFormat = SimpleDateFormat("E", Locale("ru"))
            tvDayWeek.text = dayFormat.format(date).uppercase()

            // Загрузка иконок
            val iconUrl = "https://openweathermap.org/img/wn/${forecast.weather[0].icon}.png"
            Glide.with(itemView.context)
                .load(iconUrl)
                .into(imDayWeek)

            // Температура max min
            tvMaxTemperature.text = "${forecast.main.tempMax.toInt()}°C"
            tvMinTemperature.text = "${forecast.main.tempMin.toInt()}°C"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail, parent, false)
        return DetailViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bind(detailList[position])
    }

    override fun getItemCount(): Int = detailList.size
}