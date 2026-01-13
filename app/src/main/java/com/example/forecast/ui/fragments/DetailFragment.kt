package com.example.forecast.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.forecast.ui.adapter.DetailAdapter
import com.example.forecast.ui.viewModel.DetailViewModel
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.R
import com.example.forecast.core.utils.NotificationHelper
import com.example.forecast.core.utils.PreferencesHelper
import com.example.forecast.databinding.FragmentDetailBinding
import com.example.forecast.ui.viewModel.DetailUIState
import com.example.forecast.ui.viewModel.RefreshDetailState
import com.google.android.material.snackbar.Snackbar

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
        observeRefreshState()
        observeViewModel()
        setupRetryButton()
        setupDangerousWeatherSwitch()

        viewModel.initData(cityName, requireContext())
    }

    override fun onStop() {
        super.onStop()
        viewModel.onStopFragment(requireActivity().isChangingConfigurations)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeFragment(cityName, requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Glide.with(binding.imWeather).clear(binding.imWeather)
        _binding = null
    }

    private fun setupSwipeRefresh() = with(binding) {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.onSwipeRefresh(cityName, requireContext())
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
            PreferencesHelper.isDangerousWeatherEnabled(requireContext(), cityName)
    }

    private fun handleDangerousWeatherSwitchChange() {
        binding.switchDangerousWeather.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasNotificationPermission()) {
                    PreferencesHelper.saveDangerousWeatherEnabled(
                        requireContext(),
                        cityName,
                        true
                    )
                } else {
                    requestNotificationPermissionIfNeeded()
                }
            } else {
                PreferencesHelper.saveDangerousWeatherEnabled(
                    requireContext(),
                    cityName,
                    false
                )
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_NOTIFICATION_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_NOTIFICATION_PERMISSION) {
            val granted =
                grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (granted) {
                binding.switchDangerousWeather.isChecked = true
                PreferencesHelper.saveDangerousWeatherEnabled(
                    requireContext(),
                    cityName,
                    true
                )
            } else {
                binding.switchDangerousWeather.isChecked = false
                PreferencesHelper.saveDangerousWeatherEnabled(
                    requireContext(),
                    cityName,
                    false
                )

                Snackbar.make(
                    binding.tvCity,
                    "Разрешите уведомления в настройках приложения",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATION_PERMISSION = 101
    }

    private fun observeMessageEvents() {
        viewModel.messageEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->

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
        errorCardView.visibility = View.VISIBLE
        tvError.text = state.message
        swipeRefreshLayout.isEnabled = false
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.onRetryButton(cityName, requireContext())
        }
    }
}