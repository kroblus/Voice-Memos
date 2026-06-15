package com.voicememos.aicalendar.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.voicememos.aicalendar.domain.CalendarEvent
import java.time.Instant

class ReminderScheduler(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(event: CalendarEvent) {
        ensureNotificationChannel(appContext)
        event.reminderMinutes.forEach { minutesBefore ->
            val triggerAt = event.startAt.minusSeconds(minutesBefore * 60L)
            if (triggerAt.isAfter(Instant.now())) {
                scheduleSingle(event, minutesBefore, triggerAt.toEpochMilli())
            }
        }
    }

    fun cancel(eventId: String, reminderMinutes: List<Int>) {
        reminderMinutes.forEach { minutesBefore ->
            alarmManager.cancel(pendingIntent(eventId, minutesBefore, ""))
        }
    }

    private fun scheduleSingle(event: CalendarEvent, minutesBefore: Int, triggerAtMillis: Long) {
        val pendingIntent = pendingIntent(event.id, minutesBefore, event.title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                INEXACT_WINDOW_MILLIS,
                pendingIntent,
            )
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun pendingIntent(eventId: String, minutesBefore: Int, title: String): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MINUTES_BEFORE, minutesBefore)
        }
        return PendingIntent.getBroadcast(
            appContext,
            "$eventId:$minutesBefore".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "shengli_reminders"
        private const val INEXACT_WINDOW_MILLIS = 10 * 60 * 1000L

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "声历的本地日程提醒"
            }
            manager.createNotificationChannel(channel)
        }
    }
}

