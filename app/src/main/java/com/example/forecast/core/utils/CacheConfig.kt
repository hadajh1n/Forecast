package com.example.forecast.core.utils

import java.util.concurrent.TimeUnit

object CacheConfig {
    val CACHE_VALIDITY_DURATION_MS = TimeUnit.MINUTES.toMillis(30)
    const val BACKGROUND_UPDATE_INTERVAL_HOURS = 3L
}