package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.NotificationStatus
import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import com.voicememos.aicalendar.domain.SyncStatus
import java.time.Instant
import java.util.UUID

fun CalendarEventEntity.toDomain(): CalendarEvent =
    CalendarEvent(
        id = id,
        title = title,
        notes = notes,
        startAt = Instant.ofEpochMilli(startAtEpochMillis),
        endAt = endAtEpochMillis?.let(Instant::ofEpochMilli),
        allDay = allDay,
        reminderMinutes = reminderMinutesCsv.toReminderMinutes(),
        status = runCatching { EventStatus.valueOf(status) }.getOrDefault(EventStatus.SCHEDULED),
        sourceTranscript = sourceTranscript,
        aiConfidence = aiConfidence,
        itemType = itemType.toEnumOrDefault(ScheduleItemType.EVENT),
        triggerAt = triggerAtEpochMillis?.let(Instant::ofEpochMilli),
        dueAt = dueAtEpochMillis?.let(Instant::ofEpochMilli),
        location = location,
        recurrenceRule = recurrenceRule,
        priority = priority?.toEnumOrNull<ScheduleItemPriority>(),
        notificationStatus = notificationStatus.toEnumOrDefault(NotificationStatus.PENDING),
        syncStatus = syncStatus.toEnumOrDefault(SyncStatus.LOCAL_ONLY),
        externalCalendarEventId = externalCalendarEventId,
        creationKey = creationKey,
        timeZoneId = timeZoneId,
    )

fun DraftCalendarEvent.toEntity(
    now: Instant = Instant.now(),
    creationKey: String? = null,
): CalendarEventEntity {
    val start = when (itemType) {
        ScheduleItemType.TODO -> dueAt ?: now
        ScheduleItemType.EVENT,
        ScheduleItemType.REMINDER,
        -> requireNotNull(startAt ?: triggerAt ?: dueAt) {
            "Draft must have startAt, triggerAt, or dueAt before it can be persisted."
        }
    }
    return CalendarEventEntity(
        id = UUID.randomUUID().toString(),
        title = title.trim(),
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        startAtEpochMillis = start.toEpochMilli(),
        endAtEpochMillis = endAt?.toEpochMilli(),
        allDay = allDay,
        reminderMinutesCsv = reminderMinutes.toReminderCsv(),
        status = if (itemType == ScheduleItemType.TODO && completed) {
            EventStatus.DONE.name
        } else {
            EventStatus.SCHEDULED.name
        },
        sourceTranscript = sourceTranscript,
        aiConfidence = aiConfidence.coerceIn(0.0, 1.0),
        createdAtEpochMillis = now.toEpochMilli(),
        updatedAtEpochMillis = now.toEpochMilli(),
        itemType = itemType.name,
        triggerAtEpochMillis = triggerAt?.toEpochMilli(),
        dueAtEpochMillis = dueAt?.toEpochMilli(),
        location = location.normalized(),
        recurrenceRule = recurrenceRule.normalized(),
        priority = priority?.name,
        notificationStatus = NotificationStatus.PENDING.name,
        syncStatus = SyncStatus.PENDING.name,
        externalCalendarEventId = null,
        creationKey = creationKey.normalized(),
        timeZoneId = timeZoneId.normalized(),
    )
}

fun CalendarEvent.toEntity(now: Instant = Instant.now()): CalendarEventEntity =
    CalendarEventEntity(
        id = id,
        title = title.trim(),
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        startAtEpochMillis = startAt.toEpochMilli(),
        endAtEpochMillis = endAt?.toEpochMilli(),
        allDay = allDay,
        reminderMinutesCsv = reminderMinutes.toReminderCsv(),
        status = status.name,
        sourceTranscript = sourceTranscript?.trim()?.takeIf { it.isNotEmpty() },
        aiConfidence = aiConfidence.coerceIn(0.0, 1.0),
        createdAtEpochMillis = now.toEpochMilli(),
        updatedAtEpochMillis = now.toEpochMilli(),
        itemType = itemType.name,
        triggerAtEpochMillis = triggerAt?.toEpochMilli(),
        dueAtEpochMillis = dueAt?.toEpochMilli(),
        location = location.normalized(),
        recurrenceRule = recurrenceRule.normalized(),
        priority = priority?.name,
        notificationStatus = notificationStatus.name,
        syncStatus = syncStatus.name,
        externalCalendarEventId = externalCalendarEventId.normalized(),
        creationKey = creationKey.normalized(),
        timeZoneId = timeZoneId.normalized(),
    )

fun List<Int>.toReminderCsv(): String =
    asSequence()
        .plus(0)
        .filter { it >= 0 }
        .distinct()
        .sorted()
        .joinToString(separator = ",")

fun String.toReminderMinutes(): List<Int> =
    split(",")
        .mapNotNull { raw -> raw.trim().toIntOrNull() }
        .filter { it >= 0 }
        .distinct()
        .sorted()

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
