package com.voicememos.aicalendar.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.voicememos.aicalendar.ShengliApplication
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
                val reason = when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> ReconcileReason.BOOT
                    Intent.ACTION_TIME_CHANGED -> ReconcileReason.TIME_CHANGED
                    Intent.ACTION_TIMEZONE_CHANGED -> ReconcileReason.TIME_ZONE_CHANGED
                    Intent.ACTION_MY_PACKAGE_REPLACED -> ReconcileReason.PACKAGE_UPDATED
                    else -> ReconcileReason.PERMISSION_CHANGED
                }
                (appContext as ShengliApplication).container.reminderCoordinator
                    .reconcileAll(reason)
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
