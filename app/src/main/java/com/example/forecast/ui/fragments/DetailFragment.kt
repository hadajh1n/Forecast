package com.example.forecast.ui.fragments

import android.Manifest
import android.os.Bundle
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.forecast.ui.adapter.DetailAdapter
import com.example.forecast.ui.viewModel.DetailViewModel
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.R
import com.example.forecast.core.notifications.NotificationPermissionChecker
import com.example.forecast.core.notifications.WeatherNotificationSubscription
import com.example.forecast.databinding.FragmentDetailBinding
import com.example.forecast.ui.viewModel.DetailUIState
import com.example.forecast.ui.viewModel.ErrorType
import com.example.forecast.ui.viewModel.RefreshDetailState
import com.example.forecast.ui.viewModel.UiEventDetails
import com.google.android.material.snackbar.Snackbar
import kotlin.math.round

class DetailFragment : Fragment() {

    private var _binding : FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by viewModels()
    private val detailAdapter = DetailAdapter()
    private val args: DetailFragmentArgs by navArgs()
    private val cityName: String by lazy { args.cityName }

    private val permissionChecker = NotificationPermissionChecker
    private lateinit var subscriptionManager: WeatherNotificationSubscription

    private val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { granted ->
            if (granted) {
                binding.switchDangerousWeather.isChecked = true
                subscriptionManager.subscribe(cityName)
            } else {
                binding.switchDangerousWeather.isChecked = false
                subscriptionManager.unsubscribe(cityName)
            }
        }

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

        subscriptionManager = WeatherNotificationSubscription(requireContext())

        setupRecyclerView()
        setupSwipeRefresh()
        observeMessageEvents()
        setupCityName()
        observeRefreshState()
        observeViewModel()
        setupRetryButton()
        setupDangerousWeatherSwitch()

        viewModel.initData(cityName)
    }

    override fun onStop() {
        super.onStop()
        viewModel.onStopFragment(requireActivity().isChangingConfigurations)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeFragment(cityName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Glide.with(binding.imWeather).clear(binding.imWeather)
        _binding = null
    }

    private fun setupSwipeRefresh() = with(binding) {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.onSwipeRefresh(cityName)
        }
    }

    private fun observeRefreshState() = with(binding) {
        viewModel.refreshState.observe(viewLifecycleOwner) { state ->
            when (state) {
                RefreshDetailState.Loading -> swipeRefreshLayout.post {
                    swipeRefreshLayout.isRefreshing = true
                }
                RefreshDetailState.Standard -> swipeRefreshLayout.post {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun setupDangerousWeatherSwitch() {
        loadDangerousWeatherSwitchState()
        handleDangerousWeatherSwitchChange()
    }

    private fun loadDangerousWeatherSwitchState() {
        binding.switchDangerousWeather.isChecked =
            subscriptionManager.isSubscribed(cityName)
    }

    private fun handleDangerousWeatherSwitchChange() {
        binding.switchDangerousWeather.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (permissionChecker.hasPermission(requireContext())) {
                    subscriptionManager.subscribe(cityName)
                } else {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            } else {
                subscriptionManager.unsubscribe(cityName)
            }
        }
    }

    private fun observeMessageEvents() {
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandled()?.let { message ->
                val message = when (message) {
                    UiEventDetails.ErrorRefresh ->
                        getString(R.string.error_pull_to_refresh)
                }

                val snackbar = Snackbar.make(
                    binding.tvCity,
                    message,
                    Snackbar.LENGTH_LONG
                )

                snackbar.setAnchorView(binding.tvCity)

                snackbar.setBackgroundTint(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.errorSnackbarPullToRefresh
                    )
                )

                snackbar.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        android.R.color.white
                    )
                )

                val snackbarView = snackbar.view
                val textView =
                    snackbarView.findViewById<TextView>(
                        com.google.android.material.R.id.snackbar_text
                    )

                textView.apply {
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    gravity = Gravity.CENTER
                    maxLines = 3
                }

                snackbar.show()
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
        errorCardView.visibility = View.GONE
        swipeRefreshLayout.isEnabled = false
    }

    private fun handleSuccessState(state: DetailUIState.Success) = with(binding) {
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility = View.GONE
        errorCardView.visibility = View.GONE
        swipeRefreshLayout.isEnabled = true

        if (state.forecast.isEmpty()) {
            contentContainer.visibility = View.GONE
        } else {
            contentContainer.visibility = View.VISIBLE
            tvTemperature.text = getString(
                R.string.temperature_format,
                round(state.temperature).toInt()
            )

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
        errorCardView.visibility = View.VISIBLE
        tvError.text = when (state.errorType) {
            ErrorType.FETCH_DETAILS -> getString(R.string.error_fetch_details_weather)
        }
        swipeRefreshLayout.isEnabled = false
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.onRetryButton(cityName)
        }
    }
}