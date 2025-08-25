package com.example.forecast.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.forecast.R
import com.example.forecast.adapter.DetailAdapter
import com.example.forecast.viewModel.DetailViewModel
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.forecast.Constants
import com.example.forecast.databinding.FragmentDetailBinding
import com.example.forecast.viewModel.DetailUIState

class DetailFragment : Fragment() {
    private lateinit var binding: FragmentDetailBinding
    private val viewModel: DetailViewModel by viewModels()
    private val detailAdapter = DetailAdapter()
    private lateinit var cityName : String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupCityName()
        observeViewModel()
        setupRetryButton()
        viewModel.loadCityDetail(cityName, requireContext())
    }

    private fun setupRecyclerView() {
        binding.rvWeather.adapter = detailAdapter
        binding.rvWeather.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupCityName() {
        cityName = arguments?.getString(Constants.IntentKeys.CITY_NAME) ?: getString(R.string.unknown_city)
        binding.tvCity.text = cityName
    }

    private fun observeViewModel() {
        viewModel.detailState.observe(viewLifecycleOwner) { state ->
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

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadCityDetail(cityName, requireContext())
        }
    }
}