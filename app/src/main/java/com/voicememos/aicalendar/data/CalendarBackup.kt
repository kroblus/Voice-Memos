package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

data class CalendarBackupImportResult(
    val events: List<CalendarEvent>,
    val skippedCount: Int,
)

@Serializable
private data class CalendarBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val events: List<CalendarBackupEvent> = emptyList(),
)

@Serializable
private data class CalendarBackupEvent(
    val id: String = "",
    val title: String = "",
    val notes: String? = null,
    val startAt: String = "",
    val endAt: String? = null,
    val allDay: Boolean = false,
    val reminderMinutes: List<Int> = listOf(0),
    val status: String = EventStatus.SCHEDULED.name,
    val sourceTranscript: String? = null,
    val aiConfidence: Double = 0.0,
)

fun exportCalendarBackup(
    events: List<CalendarEvent>,
    now: Instant = Instant.now(),
): String =
    BackupJson.encodeToString(
        CalendarBackupPayload(
            exportedAt = now.toString(),
            events = events.sortedBy { it.startAt }.map(CalendarEvent::toBackupEvent),
        ),
    )

fun decodeCalendarBackup(raw: String): CalendarBackupImportResult {
    val events = runCatching {
        val root = BackupJson.parseToJsonElement(raw)
        when (root) {
            is JsonObject -> BackupJson.decodeFromJsonElement<CalendarBackupPayload>(root).events
            is JsonArray -> root.map { element ->
                BackupJson.decodeFromJsonElement<CalendarBackupEvent>(element)
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    val validEvents = events.mapNotNull(CalendarBackupEvent::toDomain)
    return CalendarBackupImportResult(
        events = validEvents,
        skippedCount = events.size - validEvents.size,
    )
}

private fun CalendarEvent.toBackupEvent(): CalendarBackupEvent =
    CalendarBackupEvent(
        id = id,
        title = title,
        notes = notes,
        startAt = startAt.toString(),
        endAt = endAt?.toString(),
        allDay = allDay,
        reminderMinutes = reminderMinutes.toReminderCsv().toReminderMinutes(),
        status = status.name,
        sourceTranscript = sourceTranscript,
        aiConfidence = aiConfidence.coerceIn(0.0, 1.0),
    )

private fun CalendarBackupEvent.toDomain(): CalendarEvent? {
    val cleanId = id.trim()
    val cleanTitle = title.trim()
    val start = startAt.trim().takeIf { it.isNotEmpty() }?.let { value ->
        runCatching { Instant.parse(value) }.getOrNull()
    }
    if (cleanId.isEmpty() || cleanTitle.isEmpty() || start == null) return null
    val end = endAt?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        runCatching { Instant.parse(value) }.getOrNull()
    }
    return CalendarEvent(
        id = cleanId,
        title = cleanTitle,
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        startAt = start,
        endAt = end,
        allDay = allDay,
        reminderMinutes = reminderMinutes.toReminderCsv().toReminderMinutes(),
        status = runCatching { EventStatus.valueOf(status) }.getOrDefault(EventStatus.SCHEDULED),
        sourceTranscript = sourceTranscript?.trim()?.takeIf { it.isNotEmpty() },
        aiConfidence = aiConfidence.coerceIn(0.0, 1.0),
    )
}

private val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}
