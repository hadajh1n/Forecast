package com.example.forecast.core.utils

import java.util.concurrent.TimeUnit

object Constants {
    object Cache {
        val CACHE_VALIDITY_DURATION_MS = TimeUnit.MINUTES.toMillis(30)
    }
}