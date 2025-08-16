package com.example.forecast.activity

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.Constants
import com.example.forecast.R
import com.example.forecast.adapter.DetailAdapter
import com.example.forecast.databinding.ActivityDetailBinding
import com.example.forecast.viewmodel.DetailUIState
import com.example.forecast.viewmodel.WeatherViewModel

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private val viewModel: WeatherViewModel by viewModels()
    private val detailAdapter = DetailAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvWeather.adapter = detailAdapter
        binding.rvWeather.layoutManager = LinearLayoutManager(this)

        val cityName = intent.getStringExtra(Constants.IntentKeys.CITY_NAME) ?: getString(R.string.unknown_city)
        binding.tvCity.text = cityName

        viewModel.detailState.observe(this@DetailActivity) { state ->
            when (state) {
                is DetailUIState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.contentContainer.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                }
                is DetailUIState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE

                    if (state.forecast.isEmpty()) {
                        binding.contentContainer.visibility = View.GONE
                    } else {
                        binding.contentContainer.visibility = View.VISIBLE

                        binding.tvTemperature.text = state.temperature
                        Glide.with(this)
                            .load(state.iconUrl)
                            .into(binding.imWeather)

                        detailAdapter.detailList.clear()
                        detailAdapter.detailList.addAll(state.forecast)
                        detailAdapter.notifyDataSetChanged()
                    }
                }
                is DetailUIState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.contentContainer.visibility = View.GONE
                    binding.errorContainer.visibility = View.VISIBLE
                    binding.tvError.text = state.message
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadCityDetail(cityName, this@DetailActivity)
        }

        viewModel.loadCityDetail(cityName, this@DetailActivity)
    }
}