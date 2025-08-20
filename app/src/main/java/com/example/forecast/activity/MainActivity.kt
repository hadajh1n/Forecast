package com.example.forecast.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.Constants
import com.example.forecast.R
import com.example.forecast.adapter.CityAdapter
import com.example.forecast.databinding.ActivityMainBinding
import com.example.forecast.viewmodel.MainUIState
import com.example.forecast.viewmodel.WeatherViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: WeatherViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private val SHOW_DIALOG = "showDialog"
    private val DIALOG_INPUT_NAME = "dialogInputText"

    private val cityAdapter = CityAdapter { currentWeather ->
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(Constants.IntentKeys.CITY_NAME, currentWeather.name)
        }
        startActivity(intent)
    }

    private var dialog: AlertDialog? = null
    private var dialogInputText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvCity.adapter = cityAdapter
        binding.rvCity.layoutManager = LinearLayoutManager(this@MainActivity)

        viewModel.uiState.observe(this@MainActivity) { state ->
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

        binding.btnRetry.setOnClickListener {
            viewModel.loadCitiesFromPrefs(this@MainActivity)
        }

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
                viewModel.removeCity(city.name, this@MainActivity)
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvCity)

        viewModel.loadCitiesFromPrefs(this@MainActivity)

        binding.btnAddCity.setOnClickListener {
            showAddCityDialog()
        }

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
        val builder = AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialog)
        builder.setTitle(R.string.add_city_dialog_title)

        val input = AutoCompleteTextView(this@MainActivity)
        input.hint = getString(R.string.add_city_input_hint)
        input.isSingleLine = true
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setHintTextColor(resources.getColor(R.color.tvType3))
        input.setTextColor(resources.getColor(R.color.black))
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
        input.setPadding(padding, padding, padding, padding)

        val cities = resources.getStringArray(R.array.cities)
        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, cities)
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
                viewModel.addCity(cityName, this@MainActivity)
            }
            dialogInputText = null
            dialog?.dismiss()
            dialog = null
        }
    }
}