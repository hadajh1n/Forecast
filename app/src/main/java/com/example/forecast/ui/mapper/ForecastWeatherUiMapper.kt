package com.example.forecast.ui.mapper

import android.content.Context
import com.example.forecast.R
import com.example.forecast.data.dataclass.forecast.ForecastItemCache
import com.example.forecast.data.dataclass.forecast.ForecastWeatherCache
import com.example.forecast.data.dataclass.forecast.ForecastWeatherUI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.round

class ForecastWeatherUiMapper {

    companion object {
        private const val DAYS_TO_SHOW = 5
    }

    fun map(cache: ForecastWeatherCache): List<ForecastWeatherUI> {
        val groupedByDay = groupByDay(cache.items)
        return groupedByDay
            .take(DAYS_TO_SHOW)
            .map { dayItem ->
                ForecastWeatherUI(
                    dayOfWeek = formatDayOfWeek(dayItem.dt),
                    iconUrl = "https://openweathermap.org/img/wn/${dayItem.icon}@2x.png",
                    tempMax = round(dayItem.tempMax),
                    tempMin = round(dayItem.tempMin)
                )
            }
    }

    private fun groupByDay(items: List<ForecastItemCache>): List<ForecastItemCache> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        return items
            .groupBy { item ->
                val cal = Calendar.getInstance().apply { timeInMillis = item.dt * 1000 }
                cal.get(Calendar.DAY_OF_YEAR)
            }
            .filterKeys { it >= startDayOfYear }
            .toSortedMap()
            .map { (_, dayItems) ->
                ForecastItemCache(
                    dt = dayItems.first().dt,
                    tempMax = dayItems.maxOfOrNull { it.tempMax } ?: 0f,
                    tempMin = dayItems.minOfOrNull { it.tempMin } ?: 0f,
                    icon = dayItems[dayItems.size / 2].icon,
                    wind = null,
                    rain = null,
                    snow = null
                )
            }
    }

    private fun formatDayOfWeek(timestampSeconds: Long): String {
        return SimpleDateFormat("EEE", Locale("ru"))
            .format(Date(timestampSeconds * 1000))
            .uppercase()
            .replace(".", "")
    }
}