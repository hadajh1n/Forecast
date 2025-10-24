package com.example.forecast.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.forecast.R
import com.example.forecast.databinding.ItemDetailBinding
import com.example.forecast.data.dataclass.ForecastUI

class DetailAdapter : RecyclerView.Adapter<DetailAdapter.DetailViewHolder>() {

    val detailList = mutableListOf<ForecastUI>()

    fun updateDetails(newDetails: List<ForecastUI>) {
        val diffCallback = DetailDiffCallback(detailList, newDetails)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        detailList.clear()
        detailList.addAll(newDetails)
        diffResult.dispatchUpdatesTo(this@DetailAdapter)
    }

    inner class DetailViewHolder(view : View): RecyclerView.ViewHolder(view) {
        private val binding = ItemDetailBinding.bind(view)

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

class DetailDiffCallback(
    private val oldList: List<ForecastUI>,
    private val newList: List<ForecastUI>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].dayOfWeek == newList[newItemPosition].dayOfWeek
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        return oldItem == newItem
    }
}