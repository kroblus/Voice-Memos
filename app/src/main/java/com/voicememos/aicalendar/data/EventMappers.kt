package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
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
    )

fun DraftCalendarEvent.toEntity(now: Instant = Instant.now()): CalendarEventEntity {
    val start = requireNotNull(startAt) { "Draft must have startAt before it can be persisted." }
    return CalendarEventEntity(
        id = UUID.randomUUID().toString(),
        title = title.trim(),
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        startAtEpochMillis = start.toEpochMilli(),
        endAtEpochMillis = endAt?.toEpochMilli(),
        allDay = allDay,
        reminderMinutesCsv = reminderMinutes.toReminderCsv(),
        status = EventStatus.SCHEDULED.name,
        sourceTranscript = sourceTranscript,
        aiConfidence = aiConfidence,
        createdAtEpochMillis = now.toEpochMilli(),
        updatedAtEpochMillis = now.toEpochMilli(),
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
