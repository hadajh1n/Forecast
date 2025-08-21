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
import com.example.forecast.viewModel.DetailUIState
import com.example.forecast.viewModel.DetailViewModel

class DetailActivity : AppCompatActivity() {
    private val viewModel: DetailViewModel by viewModels()
    private lateinit var binding: ActivityDetailBinding
    private val detailAdapter = DetailAdapter()
    private lateinit var cityName : String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()     // Функция настройки RecycleView
        setupCityName()         // Функция получения и отображения имени города
        observeViewModel()      // Функция подписки на состояния viewModel (загрузка, успех, ошибка)
        setupRetryButton()      // Функция обработки кнопки для повторного подключения (ошибка)
        viewModel.loadCityDetail(cityName, this@DetailActivity) // Функция загрузки текущей погоды и прогноза на несколько дней
    }


    // Функция настройки RecycleView
    private fun setupRecyclerView() {
        binding.rvWeather.adapter = detailAdapter
        binding.rvWeather.layoutManager = LinearLayoutManager(this)
    }


    // Функция получения и отображения имени города
    private fun setupCityName() {
        cityName = intent.getStringExtra(Constants.IntentKeys.CITY_NAME) ?: getString(R.string.unknown_city)
        binding.tvCity.text = cityName
    }


    // Функция подписки на состояния viewModel (загрузка, успех, ошибка)
    private fun observeViewModel() {
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
    }


    // Функция обработки кнопки для повторного подключения (ошибка)
    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadCityDetail(cityName, this@DetailActivity)
        }
    }
}