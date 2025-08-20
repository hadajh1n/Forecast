package com.example.forecast.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.forecast.R
import com.example.forecast.databinding.ItemDetailBinding
import com.example.forecast.dataclass.ForecastUI

class DetailAdapter: RecyclerView.Adapter<DetailAdapter.DetailViewHolder>() {

    val detailList = ArrayList<ForecastUI>()

    class DetailViewHolder(item : View): RecyclerView.ViewHolder(item) {
        private val binding = ItemDetailBinding.bind(item)

        fun bind(forecast: ForecastUI) = with(binding) {
            tvDayWeek.text = forecast.dayOfWeek
            Glide.with(itemView.context)
                .load(forecast.iconUrl)
                .into(imDayWeek)
            tvMaxTemperature.text = forecast.tempMax
            tvMinTemperature.text = forecast.tempMin
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail, parent, false)
        return DetailViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bind(detailList[position])
    }

    override fun getItemCount(): Int = detailList.size
}