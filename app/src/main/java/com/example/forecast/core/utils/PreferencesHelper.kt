package com.example.forecast.core.utils

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {

    private const val PREF_NAME = "weather_prefs"
    private const val KEY_PREFIX = "dangerous_weather_switch_"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveDangerousWeatherEnabled(
        context: Context,
        cityName: String,
        isEnabled: Boolean
    ) {
        getPrefs(context).edit()
            .putBoolean(KEY_PREFIX + cityName, isEnabled)
            .apply()
    }

    fun isDangerousWeatherEnabled(context: Context, cityName: String): Boolean {
        return getPrefs(context)
            .getBoolean(KEY_PREFIX + cityName, false)
    }
}