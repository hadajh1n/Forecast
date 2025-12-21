package com.example.forecast.core.utils

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {

    private const val PREF_NAME = "weather_prefs"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveDangerousWeatherEnabled(
        context: Context,
        cityName: String,
        isEnabled: Boolean
    ) {
        getPrefs(context).edit()
            .putBoolean("dangerous_weather_switch_$cityName", isEnabled)
            .apply()
    }

    fun isDangerousWeatherEnabled(context: Context, cityName: String): Boolean {
        return getPrefs(context)
            .getBoolean("dangerous_weather_switch_$cityName", false)
    }
}