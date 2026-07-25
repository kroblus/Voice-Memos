package com.voicememos.aicalendar.reminder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.voicememos.aicalendar.data.AppDatabase
import com.voicememos.aicalendar.data.CalendarRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val repository = CalendarRepository(
                    AppDatabase.get(appContext).calendarEventDao(),
                )
                val scheduler = ReminderScheduler(appContext)
                val notificationAllowed =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                repository.futureScheduled(Instant.now())
                    .forEach { event ->
                        val results = scheduler.scheduleWithResults(event)
                        repository.updateNotificationStatus(
                            event.id,
                            results.toNotificationStatus(notificationAllowed),
                        )
                    }
            }.onFailure { error ->
                Log.e(TAG, "Unable to restore local reminders.", error)
            }
            pendingResult.finish()
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        const val TAG = "ReminderReschedule"
    }
}
