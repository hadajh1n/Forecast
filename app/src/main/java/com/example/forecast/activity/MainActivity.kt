package com.example.forecast.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.Constants
import com.example.forecast.R
import com.example.forecast.adapter.CityAdapter
import com.example.forecast.databinding.ActivityMainBinding
import com.example.forecast.viewmodel.WeatherViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: WeatherViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private val cityAdapter = CityAdapter { currentWeather ->
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(Constants.IntentKeys.CITY_NAME, currentWeather.name)
        }
        startActivity(intent)
    }

    // Ссылки на диалог
    private var dialog: AlertDialog? = null
    private var dialogInputText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация RecycleView
        binding.rvCity.adapter = cityAdapter
        binding.rvCity.layoutManager = LinearLayoutManager(this@MainActivity)

        // Подписка на список городов (WeatherViewModel)
        viewModel.cities.observe(this@MainActivity, Observer { cities ->
            cityAdapter.cityList.clear()
            cityAdapter.cityList.addAll(cities)
            cityAdapter.notifyDataSetChanged()
            binding.cvCity.visibility = if (cities.isEmpty()) View.GONE else View.VISIBLE
        })

        // Подписка на ошибки при поиске города (WeatherViewModel)
        viewModel.error.observe(this@MainActivity, Observer { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        })

        // Удаление города свайпом
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // Без перетаскивания
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val city = cityAdapter.cityList[position]
                viewModel.removeCity(city.name, this@MainActivity)
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvCity)

        // Загрузка сохраненных городов при запуске
        viewModel.loadCitiesFromPrefs(this@MainActivity)

        // Кнопка для добавления города
        binding.btnAddCity.setOnClickListener {
            showAddCityDialog()
        }

        // Восстановление состояния диалогового окна
        if (savedInstanceState != null) {
            val showDialog = savedInstanceState.getBoolean(Constants.SavedStateKeys.SHOW_DIALOG, false)
            dialogInputText = savedInstanceState.getString(Constants.SavedStateKeys.DIALOG_INPUT_NAME)
            if (showDialog) {
                showAddCityDialog()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(Constants.SavedStateKeys.SHOW_DIALOG, dialog?.isShowing == true)
        outState.putString(Constants.SavedStateKeys.DIALOG_INPUT_NAME, dialogInputText)
    }

    // Реализация диалогового окна при добавлении города
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

        builder.setView(input)
        builder.setNegativeButton(R.string.cancel_button) { dialog, _ ->
            this.dialog = null
            dialogInputText = null
            dialog.cancel()
        }

        dialogInputText?.let {
            input.setText(it)
        }

        // Сохранение текста при вводе
        input.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                dialogInputText = s?.toString()
            }
        })

        dialog = builder.create()
        dialog?.show()

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