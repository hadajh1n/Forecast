package com.example.forecast

object Constants {
    // Ключи Intent
    object IntentKeys {
        const val CITY_NAME = "CITY_NAME"
    }

    // Ключи savedInstanceState
    object SavedStateKeys {
        const val SHOW_DIALOG = "showDialog"
        const val DIALOG_INPUT_NAME = "dialogInputText"
    }

    // Ключи SharedPreferences
    const val PREFS_NAME = "WeatherAppPrefs"
    const val PREFS_KEY_CITIES = "cities"

    // Иконка погоды
    const val WEATHER_ICON_URL = "https://openweathermap.org/img/wn/%s.png"
}