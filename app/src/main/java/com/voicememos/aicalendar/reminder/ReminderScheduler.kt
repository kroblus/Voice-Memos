package com.voicememos.aicalendar.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.NotificationStatus
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.Clock
import java.time.Instant

enum class ScheduleResult {
    EXACT,
    INEXACT,
    SKIPPED_PAST,
    NOT_REQUIRED,
    FAILED,
}

internal fun ScheduleResult.toNotificationStatus(): NotificationStatus =
    when (this) {
        ScheduleResult.EXACT,
        ScheduleResult.INEXACT,
        -> NotificationStatus.SCHEDULED
        ScheduleResult.SKIPPED_PAST,
        ScheduleResult.FAILED,
        -> NotificationStatus.FAILED
        ScheduleResult.NOT_REQUIRED -> NotificationStatus.NOT_REQUIRED
    }

internal fun Map<Int, ScheduleResult>.toNotificationStatus(
    notificationAllowed: Boolean,
): NotificationStatus {
    if (isEmpty() || values.all { it == ScheduleResult.NOT_REQUIRED }) {
        return NotificationStatus.NOT_REQUIRED
    }
    if (
        !notificationAllowed ||
        values.any { it == ScheduleResult.FAILED || it == ScheduleResult.SKIPPED_PAST }
    ) {
        return NotificationStatus.FAILED
    }
    return NotificationStatus.SCHEDULED
}

internal fun CalendarEvent.reminderAnchor(): Instant? =
    when (itemType) {
        ScheduleItemType.EVENT -> startAt
        ScheduleItemType.REMINDER -> triggerAt
        ScheduleItemType.TODO -> dueAt
    }

internal fun determineScheduleResult(
    triggerAt: Instant?,
    now: Instant,
    canScheduleExact: Boolean,
): ScheduleResult {
    if (triggerAt == null) return ScheduleResult.NOT_REQUIRED
    if (!triggerAt.isAfter(now)) return ScheduleResult.SKIPPED_PAST
    return if (canScheduleExact) ScheduleResult.EXACT else ScheduleResult.INEXACT
}

interface ReminderAlarmGateway {
    fun scheduleWithResults(event: CalendarEvent): Map<Int, ScheduleResult>

    fun cancel(eventId: String, reminderMinutes: List<Int>)
}

class ReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : ReminderAlarmGateway {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(event: CalendarEvent) {
        scheduleWithResults(event)
    }

    override fun scheduleWithResults(event: CalendarEvent): Map<Int, ScheduleResult> {
        val reminderMinutes = event.reminderMinutes.distinct()
        if (reminderMinutes.isEmpty()) return emptyMap()
        val anchor = event.reminderAnchor()
            ?: return reminderMinutes.associateWith { ScheduleResult.NOT_REQUIRED }
        if (runCatching { ensureNotificationChannel(appContext) }.isFailure) {
            return reminderMinutes.associateWith { ScheduleResult.FAILED }
        }

        val now = clock.instant()
        return reminderMinutes.associateWith { minutesBefore ->
            val triggerAt = anchor.minusSeconds(minutesBefore * 60L)
            scheduleSingle(event, minutesBefore, triggerAt, now)
        }
    }

    override fun cancel(eventId: String, reminderMinutes: List<Int>) {
        reminderMinutes.forEach { minutesBefore ->
            alarmManager.cancel(pendingIntent(eventId, minutesBefore, ""))
        }
    }

    private fun scheduleSingle(
        event: CalendarEvent,
        minutesBefore: Int,
        triggerAt: Instant,
        now: Instant,
    ): ScheduleResult = runCatching {
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        val result = determineScheduleResult(
            triggerAt = triggerAt,
            now = now,
            canScheduleExact = canScheduleExact,
        )
        if (
            result == ScheduleResult.SKIPPED_PAST ||
            result == ScheduleResult.NOT_REQUIRED
        ) {
            return@runCatching result
        }

        val pendingIntent = pendingIntent(event.id, minutesBefore, event.title)
        when (result) {
            ScheduleResult.EXACT -> alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toEpochMilli(),
                pendingIntent,
            )
            ScheduleResult.INEXACT -> alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toEpochMilli(),
                INEXACT_WINDOW_MILLIS,
                pendingIntent,
            )
            ScheduleResult.SKIPPED_PAST,
            ScheduleResult.NOT_REQUIRED,
            ScheduleResult.FAILED,
            -> Unit
        }
        result
    }.getOrDefault(ScheduleResult.FAILED)

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
        const val CHANNEL_ID = "shengli_local_fallback_v2"
        private const val INEXACT_WINDOW_MILLIS = 10 * 60 * 1000L

        fun ensureNotificationChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "系统日历不可用时，由声历直接发送的备用提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 240, 120, 240)
                setBypassDnd(false)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            manager.createNotificationChannel(channel)
        }
    }
}
