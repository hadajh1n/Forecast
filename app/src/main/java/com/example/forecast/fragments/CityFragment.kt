package com.example.forecast.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.Constants
import com.example.forecast.R
import com.example.forecast.adapter.CityAdapter
import com.example.forecast.databinding.FragmentCityBinding
import com.example.forecast.viewModel.MainUIState
import com.example.forecast.viewModel.MainViewModel

class CityFragment : Fragment() {
    private lateinit var binding: FragmentCityBinding
    private val viewModel: MainViewModel by viewModels()

    private val cityAdapter = CityAdapter { currentWeather ->
        val detailFragment = DetailFragment().apply {
            arguments = Bundle().apply {
                putString(Constants.IntentKeys.CITY_NAME, currentWeather.name)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        private const val SHOW_DIALOG = "showDialog"
        private const val DIALOG_INPUT_NAME = "dialogInputText"
    }

    private var dialog: AlertDialog? = null
    private var dialogInputText: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModelState()
        observeMessageError()
        setupItemTouchHelper()
        setupAddCityButton()
        setupRetryButton()
        restoreDialogState(savedInstanceState)
        viewModel.loadCitiesFromPrefs(requireContext())
    }

    private fun setupRecyclerView() {
        binding.rvCity.adapter = cityAdapter
        binding.rvCity.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observeViewModelState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MainUIState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.cvCity.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                    binding.tvAddFirstCity.visibility = View.GONE
                    binding.btnAddCity.visibility = View.GONE
                }
                is MainUIState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                    binding.cvCity.visibility = if (state.cities.isEmpty()) View.GONE else View.VISIBLE
                    binding.tvAddFirstCity.visibility = if (state.cities.isEmpty()) View.VISIBLE else View.GONE
                    binding.btnAddCity.visibility = View.VISIBLE

                    if (cityAdapter.cityList != state.cities) {
                        cityAdapter.cityList.clear()
                        cityAdapter.cityList.addAll(state.cities)
                        cityAdapter.notifyDataSetChanged()
                    }
                }
                is MainUIState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.cvCity.visibility = View.GONE
                    binding.btnAddCity.visibility = View.GONE
                    binding.errorContainer.visibility = View.VISIBLE
                    binding.tvAddFirstCity.visibility = View.GONE
                    binding.tvErrorLoadCities.text = state.message
                }
            }
        }
    }

    private fun observeMessageError() {
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val city = cityAdapter.cityList[position]
                cityAdapter.cityList.removeAt(position)
                cityAdapter.notifyItemRemoved(position)
                viewModel.removeCity(city.name, requireContext())
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvCity)
    }

    private fun setupAddCityButton() {
        binding.btnAddCity.setOnClickListener {
            showAddCityDialog()
        }
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadCitiesFromPrefs(requireContext())
        }
    }

    private fun restoreDialogState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val showDialog = savedInstanceState.getBoolean(SHOW_DIALOG, false)
            dialogInputText = savedInstanceState.getString(DIALOG_INPUT_NAME)
            if (showDialog) {
                showAddCityDialog()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SHOW_DIALOG, dialog?.isShowing == true)
        outState.putString(DIALOG_INPUT_NAME, dialogInputText)
    }

    private fun showAddCityDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        builder.setTitle(R.string.add_city_dialog_title)

        val input = AutoCompleteTextView(requireContext())
        input.hint = getString(R.string.add_city_input_hint)
        input.isSingleLine = true
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setHintTextColor(resources.getColor(R.color.tvType3))
        input.setTextColor(resources.getColor(R.color.black))
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
        input.setPadding(padding, padding, padding, padding)

        val cities = resources.getStringArray(R.array.cities)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, cities)
        input.setAdapter(adapter)
        input.threshold = 1

        dialogInputText?.let {
            input.setText(it)
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                dialogInputText = s?.toString()
            }
        })

        builder.setView(input)
        builder.setNegativeButton(R.string.cancel_button) { dialog, _ ->
            this.dialog = null
            dialogInputText = null
            dialog.cancel()
        }

        dialog = builder.create().apply {
            setCanceledOnTouchOutside(false)
            setCancelable(false)
            show()

            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(resources.getColor(R.color.black))
        }

        input.setOnItemClickListener { _, _, position, _ ->
            val cityName = adapter.getItem(position).toString().trim()
            if (cityName.isNotEmpty()) {
                viewModel.addCity(cityName, requireContext())
            }
            dialogInputText = null
            dialog?.dismiss()
            dialog = null
        }
    }
}
