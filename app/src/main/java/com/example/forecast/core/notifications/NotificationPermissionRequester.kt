package com.example.forecast.core.notifications

import android.Manifest
import android.os.Build
import androidx.fragment.app.Fragment

class NotificationPermissionRequester(private val fragment: Fragment) {

    fun requestPermissionIfNeeded(requestCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (!NotificationPermissionChecker.hasPermission(fragment.requireContext())) {
            fragment.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }
}