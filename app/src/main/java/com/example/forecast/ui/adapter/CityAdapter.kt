package com.example.forecast.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.R
import com.example.forecast.databinding.ItemCityBinding
import com.example.forecast.data.dataclass.current.CurrentWeatherUI
import com.example.forecast.databinding.ItemAddButtonBinding
import com.example.forecast.databinding.ItemAddFirstCityBinding
import com.example.forecast.databinding.ItemLoadingBinding
import kotlin.math.round

sealed class CityAdapterItem {

    data class City(val data: CurrentWeatherUI) : CityAdapterItem()
    object AddFirst : CityAdapterItem()
    object AddButton : CityAdapterItem()
    object Loading : CityAdapterItem()
}

class CityAdapter(
    private val onItemClick: (CurrentWeatherUI) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<CityAdapterItem>()
    private var isLoadingFooterVisible = false

    companion object {
        private const val TYPE_ADD_FIRST = 0
        private const val TYPE_CITY = 1
        private const val TYPE_ADD_BUTTON = 2
        private const val TYPE_LOADING = 3
    }

    fun updateCities(newCities: List<CurrentWeatherUI>) {
        val newItems = buildList {
            if (newCities.isEmpty()) {
                add(CityAdapterItem.AddFirst)
            }

            newCities.forEach {
                add(CityAdapterItem.City(it))
            }

            if (isLoadingFooterVisible) {
                add(CityAdapterItem.Loading)
            }

            add(CityAdapterItem.AddButton)
        }

        val diffResult = DiffUtil.calculateDiff(
            CityDiffCallback(items, newItems)
        )

        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this@CityAdapter)
    }

    fun removeCity(position: Int): CurrentWeatherUI {
        val item = items[position] as CityAdapterItem.City
        val city = item.data
        items.removeAt(position)
        if (items.none { it is CityAdapterItem.City }) items.add(0, CityAdapterItem.AddFirst)
        notifyDataSetChanged()
        return city
    }

    fun showLoadingFooter() {
        if (!isLoadingFooterVisible) {
            isLoadingFooterVisible = true
            items.add(CityAdapterItem.Loading)
            notifyItemInserted(items.lastIndex)
        }
    }

    fun hideLoadingFooter() {
        if (isLoadingFooterVisible) {
            val index = items.indexOfLast { it is CityAdapterItem.Loading }
            if (index != -1) {
                items.removeAt(index)
                notifyItemRemoved(index)
            }
            isLoadingFooterVisible = false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ADD_FIRST -> AddFirstViewHolder(
                ItemAddFirstCityBinding.inflate(inflater, parent, false),
                onAddClick
            )

            TYPE_CITY -> CityViewHolder(
                ItemCityBinding.inflate(inflater, parent, false),
                onItemClick
            )

            TYPE_ADD_BUTTON -> AddButtonViewHolder(
                ItemAddButtonBinding.inflate(inflater, parent, false),
                onAddClick
            )

            TYPE_LOADING -> LoadingViewHolder(
                ItemLoadingBinding.inflate(inflater, parent, false)
            )

            else -> error("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CityAdapterItem.AddFirst -> (holder as AddFirstViewHolder).bind()
            is CityAdapterItem.City -> (holder as CityViewHolder).bind(item.data)
            is CityAdapterItem.AddButton -> (holder as AddButtonViewHolder).bind()
            is CityAdapterItem.Loading -> {}
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is CityAdapterItem.AddFirst -> TYPE_ADD_FIRST
            is CityAdapterItem.City -> TYPE_CITY
            is CityAdapterItem.AddButton -> TYPE_ADD_BUTTON
            is CityAdapterItem.Loading -> TYPE_LOADING
        }

    class AddFirstViewHolder(
        private val binding: ItemAddFirstCityBinding,
        private val onAddClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.setOnClickListener { onAddClick() }
        }
    }

    class CityViewHolder(
        private val binding: ItemCityBinding,
        private val onItemClick: (CurrentWeatherUI) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(current: CurrentWeatherUI) = with(binding) {
            val context = root.context
            tvCity.text = current.cityName
            tvTemperature.text = context.getString(
                R.string.temperature_format,
                round(current.temp).toInt()
            )
            root.setOnClickListener { onItemClick(current) }
        }
    }

    class AddButtonViewHolder(
        private val binding: ItemAddButtonBinding,
        private val onAddClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.btnAddCity.setOnClickListener { onAddClick() }
        }
    }

    class LoadingViewHolder(
        binding: ItemLoadingBinding
    ) : RecyclerView.ViewHolder(binding.root)
}

class CityDiffCallback(
    private val oldList: List<CityAdapterItem>,
    private val newList: List<CityAdapterItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]

        return when {
            old is CityAdapterItem.City && new is CityAdapterItem.City ->
                old.data.cityName == new.data.cityName
            else -> old::class == new::class
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}