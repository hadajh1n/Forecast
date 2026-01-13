package com.example.forecast.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.forecast.worker.DangerousWeatherWorker

class DangerousWeatherAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DangerousWeatherWorker"
        private const val ALARM_REQUEST_CODE = 1001
        private const val ALARM_INTERVAL_MS = 8 * 60 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "ALARM сработал! Время: ${System.currentTimeMillis()}")

        // Запускаем Worker
        val workRequest = OneTimeWorkRequestBuilder<DangerousWeatherWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "Worker поставлен в очередь")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextIntent = Intent(context, DangerousWeatherAlarmReceiver::class.java)
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTime = System.currentTimeMillis() + ALARM_INTERVAL_MS
        Log.d(TAG, "Планирование следующего Alarm на $nextTime")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTime,
                nextPendingIntent
            )
        } else {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTime,
                ALARM_INTERVAL_MS,
                nextPendingIntent
            )
        }
        Log.d(TAG, "Следующий Alarm запланирован")
    }
}