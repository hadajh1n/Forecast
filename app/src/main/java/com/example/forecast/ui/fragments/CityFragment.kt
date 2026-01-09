package com.example.forecast.ui.fragments

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.R
import com.example.forecast.ui.adapter.CityAdapter
import com.example.forecast.databinding.FragmentCityBinding
import com.example.forecast.ui.viewModel.CitiesState
import com.example.forecast.ui.viewModel.MainUIState
import com.example.forecast.ui.viewModel.MainViewModel
import com.example.forecast.ui.viewModel.RefreshCityState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CityFragment : Fragment() {

    companion object {
        private const val SHOW_DIALOG = "showDialog"
        private const val DIALOG_INPUT_NAME = "dialogInputText"
    }

    private var _binding: FragmentCityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()

    private val cityAdapter = CityAdapter { currentWeather ->
        try {
            val action = CityFragmentDirections
                .actionCityFragmentToDetailFragment(currentWeather.cityName)
            findNavController().navigate(action)
        } catch (e: IllegalStateException) {
        }
    }

    private var dialog: AlertDialog? = null
    private var dialogInputText: String? = null
    private var dialogInput: AutoCompleteTextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModelState()
        observeRefreshState()
        observeCitiesState()
        observeMessageError()
        setupItemTouchHelper()
        setupAddCityCardView()
        setupAddCityButton()
        restoreDialogState(savedInstanceState)

        viewModel.initData(requireContext())
    }

    override fun onStart() {
        super.onStart()
        viewModel.onRefreshBackground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onStopFragment(requireActivity().isChangingConfigurations)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeFragment(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialog?.dismiss()
        dialog = null
        dialogInput?.removeTextChangedListener(textWatcher)
        dialogInput = null
        _binding = null
    }

    private fun observeRefreshState() = with(binding) {
        viewModel.refreshState.observe(viewLifecycleOwner) { state ->
            when (state) {
                RefreshCityState.Loading -> swipeRefreshLayout.post {
                    swipeRefreshLayout.isRefreshing = true
                    btnAddCity.visibility = View.GONE
                }
                RefreshCityState.Standard -> swipeRefreshLayout.post {
                    swipeRefreshLayout.isRefreshing = false
                    btnAddCity.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupSwipeRefresh() = with(binding) {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.onSwipeRefresh(requireContext())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SHOW_DIALOG, dialog?.isShowing == true)
        outState.putString(DIALOG_INPUT_NAME, dialogInput?.text?.toString() ?: dialogInputText)
    }

    private fun setupRecyclerView() = with(binding) {
        rvCity.adapter = cityAdapter
        rvCity.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observeCitiesState() = with(binding) {
        viewModel.citiesState.observe(viewLifecycleOwner) { state ->
            when (state) {
                CitiesState.Standard -> {
                    cityAdapter.hideLoadingFooter()
                    swipeRefreshLayout.isEnabled = true
                }
                CitiesState.Loading -> {
                    cvAddFirstCity.visibility = View.GONE
                    cvCity.visibility = View.VISIBLE
                    cityAdapter.showLoadingFooter()
                    swipeRefreshLayout.isEnabled = false
                }
            }
        }
    }

    private fun observeViewModelState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MainUIState.Loading -> handleLoadingState()
                is MainUIState.Success -> handleSuccessState(state)
            }
        }
    }

    private fun handleLoadingState() = with(binding) {
        progressBar.visibility = View.VISIBLE
        cvCity.visibility = View.GONE
        cvError.visibility = View.GONE
        cvAddFirstCity.visibility = View.GONE
        btnAddCity.visibility = View.GONE
        swipeRefreshLayout.isEnabled = false
    }

    private fun handleSuccessState(state: MainUIState.Success) = with(binding) {
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility = View.GONE
        cvError.visibility = View.GONE
        cvCity.visibility = if (state.cities.isEmpty()) View.GONE else View.VISIBLE
        cvAddFirstCity.visibility = if (state.cities.isEmpty()) View.VISIBLE else View.GONE
        btnAddCity.visibility = View.VISIBLE
        cityAdapter.updateCities(state.cities)
        swipeRefreshLayout.isEnabled = if (state.cities.isEmpty()) false else true
    }

    private fun observeMessageError() {
        viewModel.messageEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).apply {
                    setBackgroundTint(ContextCompat.getColor(
                        requireContext(),
                        R.color.errorSnackbarPullToRefresh)
                    )
                    setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                    show()
                }
            }
        }
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val city = cityAdapter.removeCity(position)

                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.onSwipeRemove(city.cityName)
                }
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.rvCity)
    }

    private fun setupAddCityCardView() {
        binding.cvAddFirstCity.setOnClickListener {
            showAddCityDialog()
        }
    }

    private fun setupAddCityButton() {
        binding.btnAddCity.setOnClickListener {
            showAddCityDialog()
        }
    }

    private fun restoreDialogState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            dialogInputText = savedInstanceState.getString(DIALOG_INPUT_NAME)
            if (savedInstanceState.getBoolean(SHOW_DIALOG, false)) {
                showAddCityDialog()
            }
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {
            dialogInputText = s?.toString()
        }
    }

    private fun showAddCityDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        builder.setTitle(R.string.add_city_dialog_title)
        dialogInput = AutoCompleteTextView(requireContext()).apply {
            hint = getString(R.string.add_city_input_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setHintTextColor(resources.getColor(R.color.tvType3))
            setTextColor(resources.getColor(R.color.black))
            val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
            setPadding(padding, padding, padding, padding)
            val cities = resources.getStringArray(R.array.cities)
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                cities
            )
            setAdapter(adapter)
            threshold = 1

            if (!dialogInputText.isNullOrEmpty()) {
                setText(dialogInputText)
                setSelection(dialogInputText!!.length)
                if (dialogInputText!!.length >= threshold) {
                    post { showDropDown() }
                }
            }

            addTextChangedListener(textWatcher)
            requestFocus()
            post {
                val imm = requireContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        builder.setView(dialogInput)

        builder.setNegativeButton(R.string.cancel_button) { _, _ ->
            dialogInputText = null
            dialogInput?.removeTextChangedListener(textWatcher)
            dialogInput = null
            dialog = null
        }

        dialog = builder.create().apply {
            setCanceledOnTouchOutside(false)
            setCancelable(false)
            setOnDismissListener {
                dialogInput?.removeTextChangedListener(textWatcher)
                dialogInput = null
                dialog = null
            }
            show()
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(resources.getColor(R.color.black))
        }

        dialogInput?.setOnItemClickListener { _, _, position, _ ->
            val cityName = dialogInput?.adapter?.getItem(position).toString().trim()
            if (cityName.isNotEmpty()) {
                viewModel.onAddNewCity(cityName, requireContext())
            }

            dialogInputText = null
            dialogInput?.removeTextChangedListener(textWatcher)
            dialogInput = null
            dialog?.dismiss()
            dialog = null
        }
    }
}