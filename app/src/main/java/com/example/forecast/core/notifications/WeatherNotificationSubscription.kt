package com.example.forecast.core.notifications

import android.content.Context
import com.example.forecast.core.utils.PreferencesHelper

class WeatherNotificationSubscription(private val context: Context) {

    fun isSubscribed(city: String): Boolean {
        return PreferencesHelper.isDangerousWeatherEnabled(context, city)
    }

    fun subscribe(city: String) {
        PreferencesHelper.saveDangerousWeatherEnabled(context, city, true)
    }

    fun unsubscribe(city: String) {
        PreferencesHelper.saveDangerousWeatherEnabled(context, city, false)
    }
}