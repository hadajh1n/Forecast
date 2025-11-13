package com.example.forecast.ui.fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.example.forecast.ui.adapter.DetailAdapter
import com.example.forecast.ui.viewModel.DetailViewModel
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.R
import com.example.forecast.core.utils.NotificationHelper
import com.example.forecast.core.utils.PreferencesHelper
import com.example.forecast.databinding.FragmentDetailBinding
import com.example.forecast.network.retrofit.RetrofitClient
import com.example.forecast.ui.viewModel.DetailUIState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class DetailFragment : Fragment() {

    private var _binding : FragmentDetailBinding? = null

    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by viewModels()
    private val detailAdapter = DetailAdapter()
    private val args: DetailFragmentArgs by navArgs()
    private val cityName: String by lazy { args.cityName }
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notificationHelper = NotificationHelper(requireContext())

        setupRecyclerView()
        setupSwipeRefresh()
        observeMessageEvents()
        setupCityName()
        observeViewModel()
        setupRetryButton()
        setupDangerousWeatherSwitch()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadCityDetail(cityName, requireContext())
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startRefresh(cityName, requireActivity().applicationContext)
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopRefresh()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Glide.with(binding.imWeather).clear(binding.imWeather)
        _binding = null
    }

    private fun setupSwipeRefresh() = with(binding) {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshDetailsSwipe(cityName, requireContext())
        }
    }

    private fun setupDangerousWeatherSwitch() {
        loadDangerousWeatherSwitchState()
        handleDangerousWeatherSwitchChange()
    }

    private fun loadDangerousWeatherSwitchState() {
        binding.switchDangerousWeather.isChecked =
            PreferencesHelper.isDangerousWeatherEnabled(requireContext(), cityName)
    }

    private fun handleDangerousWeatherSwitchChange() {
        binding.switchDangerousWeather.setOnCheckedChangeListener { _, isChecked ->
            PreferencesHelper.saveDangerousWeatherEnabled(requireContext(), cityName, isChecked)

            if (isChecked) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val dangerousEvents = getDangerousWeather(cityName, requireContext())

                    if (dangerousEvents.isNotEmpty()) {
                        val title = requireContext().getString(R.string.dangerous_weather_title)
                        val message = requireContext().getString(
                            R.string.dangerous_weather_message,
                            cityName,
                            dangerousEvents.joinToString(", ")
                        )

                        notificationHelper.sendNotification(title, message, cityName)
                    }
                }
            }
        }
    }

    private suspend fun getDangerousWeather(city: String, context: Context): List<String> {
        return try {
            val response = RetrofitClient.weatherApi.getCurrentWeather(city)

            val dangerousEvents = mutableListOf<String>()
            val temp = response.main.temp
            val windSpeed = response.wind?.speed ?: 0f
            val description = response.weather.firstOrNull()?.description?.lowercase() ?: ""

            if (temp <= -20) {
                dangerousEvents += context.getString(R.string.low_temperature_warning, temp.toInt())
            }

            if (temp >= 35) {
                dangerousEvents += context.getString(R.string.heat_warning, temp.toInt())
            }

            if (windSpeed >= 15) {
                dangerousEvents += context.getString(R.string.strong_wind_warning, windSpeed.toInt())
            }

            if (listOf("гроза", "ураган", "ливень", "снегопад").any { it in description }) {
                dangerousEvents += context.getString(R.string.storm_warning, description)
            }

            dangerousEvents
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun observeMessageEvents() {
        viewModel.messageEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                 Snackbar.make(binding.tvCity, message, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.tvCity)
                    .setBackgroundTint(ContextCompat.getColor(
                        requireContext(),
                        R.color.errorSnackbarPullToRefresh)
                    )
                    .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                    .show()
            }
        }
    }

    private fun setupRecyclerView() = with(binding) {
        rvWeather.adapter = detailAdapter
        rvWeather.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupCityName() {
        binding.tvCity.text = cityName.takeIf { it.isNotEmpty() }
            ?: getString(R.string.unknown_city)
    }

    private fun observeViewModel() {
        viewModel.detailState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DetailUIState.Loading -> handleLoadingState()
                is DetailUIState.Success -> handleSuccessState(state)
                is DetailUIState.Error -> handleErrorState(state)
            }
        }
    }

    private fun handleLoadingState() = with(binding) {
        progressBar.visibility = View.VISIBLE
        contentContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        swipeRefreshLayout.isEnabled = false
    }

    private fun handleSuccessState(state: DetailUIState.Success) = with(binding) {
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility = View.GONE
        errorContainer.visibility = View.GONE
        swipeRefreshLayout.isEnabled = true

        if (state.forecast.isEmpty()) {
            contentContainer.visibility = View.GONE
        } else {
            contentContainer.visibility = View.VISIBLE
            tvTemperature.text = state.temperature

            Glide.with(this@DetailFragment)
                .load(state.iconUrl)
                .into(imWeather)

            detailAdapter.updateDetails(state.forecast)
        }
    }

    private fun handleErrorState(state: DetailUIState.Error) = with(binding) {
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility = View.GONE
        contentContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        tvError.text = state.message
        swipeRefreshLayout.isEnabled = false
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.loadCityDetail(cityName, requireContext())
            }
        }
    }
}