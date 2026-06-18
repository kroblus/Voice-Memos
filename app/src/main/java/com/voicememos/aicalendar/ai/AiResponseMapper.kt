package com.voicememos.aicalendar.ai

import com.voicememos.aicalendar.domain.AiParseResult
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.RepeatFrequency
import com.voicememos.aicalendar.domain.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

object AiResponseMapper {
    fun map(
        response: AiCalendarResponse,
        transcript: String,
        zoneId: ZoneId,
        defaultDurationMinutes: Int,
        defaultReminderMinutes: Int,
    ): AiParseResult {
        val warnings = response.warnings.toMutableList()
        val drafts = response.events.mapNotNull { event ->
            val title = event.title.trim()
            if (title.isEmpty()) {
                warnings += "有一个事件缺少标题，已忽略。"
                return@mapNotNull null
            }

            val start = parseInstant(event.startLocal, event.allDay, zoneId)
            val end = parseInstant(event.endLocal, event.allDay, zoneId)
                ?: start?.takeIf { !event.allDay }?.plusSeconds(defaultDurationMinutes.coerceAtLeast(1) * 60L)
            val missing = event.missingFields.toMutableSet()
            if (start == null) missing += "start_local"

            DraftCalendarEvent(
                title = title,
                notes = event.notes?.trim()?.takeIf { it.isNotEmpty() },
                startAt = start,
                endAt = end,
                allDay = event.allDay,
                reminderMinutes = event.reminderMinutes
                    .ifEmpty { listOf(defaultReminderMinutes) }
                    .plus(0)
                    .filter { it >= 0 }
                    .distinct()
                    .sorted(),
                sourceTranscript = transcript,
                aiConfidence = event.confidence.coerceIn(0.0, 1.0),
                missingFields = missing.sorted(),
                repeatRule = event.toRepeatRule(),
            )
        }

        return AiParseResult(
            events = drafts,
            warnings = warnings.distinct(),
            // Voice-created events stay user-confirmed in the first release,
            // even when all fields are complete and confidence is high.
            needsConfirmation = true,
        )
    }

    private fun parseInstant(value: String?, allDay: Boolean, zoneId: ZoneId): Instant? {
        val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return parseOffset(cleaned)
            ?: parseLocalDateTime(cleaned, zoneId)
            ?: parseDate(cleaned, allDay, zoneId)
    }

    private fun parseOffset(value: String): Instant? =
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }

    private fun parseLocalDateTime(value: String, zoneId: ZoneId): Instant? =
        try {
            LocalDateTime.parse(value).atZone(zoneId).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }

    private fun parseDate(value: String, allDay: Boolean, zoneId: ZoneId): Instant? =
        if (!allDay) {
            null
        } else {
            try {
                LocalDate.parse(value).atStartOfDay(zoneId).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }

    private fun AiCalendarEvent.toRepeatRule(): RepeatRule? {
        val frequency = when (repeatFrequency?.trim()?.lowercase()) {
            "daily", "day", "每天" -> RepeatFrequency.DAILY
            "weekly", "week", "每周" -> RepeatFrequency.WEEKLY
            else -> return null
        }
        val defaultCount = when (frequency) {
            RepeatFrequency.DAILY -> 7
            RepeatFrequency.WEEKLY -> 8
        }
        return RepeatRule(
            frequency = frequency,
            count = repeatCount ?: defaultCount,
        )
    }
}
