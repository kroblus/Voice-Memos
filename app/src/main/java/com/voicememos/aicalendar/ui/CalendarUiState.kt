package com.voicememos.aicalendar.ui

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class CalendarViewMode(val label: String) {
    MONTH("月"),
    WEEK("周"),
    DAY("日"),
    AGENDA("清单"),
}

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val events: List<CalendarEvent> = emptyList(),
    val textInput: String = "",
    val partialTranscript: String = "",
    val isListening: Boolean = false,
    val isParsing: Boolean = false,
    val draftEditors: List<DraftEditor> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null,
    val showSettings: Boolean = false,
    val apiKeyInput: String = "",
    val apiKeySaved: Boolean = false,
)

data class DraftEditor(
    val draftId: String,
    val title: String,
    val notes: String,
    val startLocal: String,
    val endLocal: String,
    val allDay: Boolean,
    val reminderMinutes: String,
    val sourceTranscript: String,
    val aiConfidence: Double,
    val missingFields: List<String>,
) {
    fun toDraft(zoneId: ZoneId): DraftCalendarEvent {
        val start = parseLocalInstant(startLocal, allDay, zoneId)
        val end = parseLocalInstant(endLocal, allDay, zoneId)
        val missing = missingFields.toMutableSet()
        if (start == null) missing += "start_local" else missing.remove("start_local")
        return DraftCalendarEvent(
            draftId = draftId,
            title = title,
            notes = notes.takeIf { it.isNotBlank() },
            startAt = start,
            endAt = end,
            allDay = allDay,
            reminderMinutes = reminderMinutes.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it >= 0 }
                .ifEmpty { listOf(10) },
            sourceTranscript = sourceTranscript,
            aiConfidence = aiConfidence,
            missingFields = missing.sorted(),
        )
    }
}

fun DraftCalendarEvent.toEditor(zoneId: ZoneId): DraftEditor =
    DraftEditor(
        draftId = draftId,
        title = title,
        notes = notes.orEmpty(),
        startLocal = startAt?.formatForEditor(allDay, zoneId).orEmpty(),
        endLocal = endAt?.formatForEditor(allDay, zoneId).orEmpty(),
        allDay = allDay,
        reminderMinutes = reminderMinutes.joinToString(","),
        sourceTranscript = sourceTranscript,
        aiConfidence = aiConfidence,
        missingFields = missingFields,
    )

private fun Instant.formatForEditor(allDay: Boolean, zoneId: ZoneId): String {
    val zoned = atZone(zoneId)
    return if (allDay) {
        zoned.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    } else {
        zoned.toLocalDateTime().format(EditorDateTimeFormatter)
    }
}

private fun parseLocalInstant(value: String, allDay: Boolean, zoneId: ZoneId): Instant? {
    val cleaned = value.trim()
    if (cleaned.isEmpty()) return null
    return if (allDay) {
        runCatching { LocalDate.parse(cleaned).atStartOfDay(zoneId).toInstant() }.getOrNull()
    } else {
        parseDateTime(cleaned, zoneId)
    }
}

private fun parseDateTime(value: String, zoneId: ZoneId): Instant? {
    val normalized = value.replace("T", " ")
    return try {
        LocalDateTime.parse(normalized, EditorDateTimeFormatter).atZone(zoneId).toInstant()
    } catch (_: DateTimeParseException) {
        runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
    }
}

private val EditorDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

