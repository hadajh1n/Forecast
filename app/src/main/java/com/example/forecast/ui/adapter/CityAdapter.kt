package com.example.forecast.ui.adapter

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
    private var currentCities: List<CurrentWeatherUI> = emptyList()
    private var isLoadingFooterVisible = false

    companion object {
        private const val TYPE_ADD_FIRST = 0
        private const val TYPE_CITY = 1
        private const val TYPE_LOADING = 2
        private const val TYPE_ADD_BUTTON = 3
    }

    fun updateCities(newCities: List<CurrentWeatherUI>) {
        currentCities = newCities
        rebuildItems()
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
            updateCities(currentCities)
        }
    }

    fun hideLoadingFooter() {
        if (isLoadingFooterVisible) {
            isLoadingFooterVisible = false
            updateCities(currentCities)
        }
    }

    private fun rebuildItems() {
        val newItems = buildList {
            if (currentCities.isEmpty()) add(CityAdapterItem.AddFirst)

            currentCities.forEach { add(CityAdapterItem.City(it)) }

            if (isLoadingFooterVisible) add(CityAdapterItem.Loading)

            add(CityAdapterItem.AddButton)
        }

        val diffResult = DiffUtil.calculateDiff(CityDiffCallback(items, newItems))
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this@CityAdapter)
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
            TYPE_LOADING -> LoadingViewHolder(
                ItemLoadingBinding.inflate(inflater, parent, false)
            )
            TYPE_ADD_BUTTON -> AddButtonViewHolder(
                ItemAddButtonBinding.inflate(inflater, parent, false),
                onAddClick
            )
            else -> error("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CityAdapterItem.AddFirst -> (holder as AddFirstViewHolder).bind()
            is CityAdapterItem.City -> (holder as CityViewHolder).bind(item.data)
            is CityAdapterItem.Loading -> Unit
            is CityAdapterItem.AddButton -> (holder as AddButtonViewHolder).bind()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is CityAdapterItem.AddFirst -> TYPE_ADD_FIRST
            is CityAdapterItem.City -> TYPE_CITY
            is CityAdapterItem.Loading -> TYPE_LOADING
            is CityAdapterItem.AddButton -> TYPE_ADD_BUTTON
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

    class LoadingViewHolder(
        binding: ItemLoadingBinding
    ) : RecyclerView.ViewHolder(binding.root)

    class AddButtonViewHolder(
        private val binding: ItemAddButtonBinding,
        private val onAddClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.btnAddCity.setOnClickListener { onAddClick() }
        }
    }
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