package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun exportEventsJson(events: List<CalendarEvent>): String =
    events.joinToString(
        prefix = "[\n",
        postfix = "\n]",
        separator = ",\n",
    ) { event ->
        """
        |  {
        |    "id": "${event.id.escapeJson()}",
        |    "title": "${event.title.escapeJson()}",
        |    "notes": ${event.notes?.let { "\"${it.escapeJson()}\"" } ?: "null"},
        |    "startAt": "${event.startAt}",
        |    "endAt": ${event.endAt?.let { "\"$it\"" } ?: "null"},
        |    "allDay": ${event.allDay},
        |    "reminderMinutes": [${event.reminderMinutes.joinToString(",")}],
        |    "status": "${event.status.name}",
        |    "sourceTranscript": ${event.sourceTranscript?.let { "\"${it.escapeJson()}\"" } ?: "null"},
        |    "aiConfidence": ${event.aiConfidence},
        |    "itemType": "${event.itemType.name}",
        |    "triggerAt": ${event.triggerAt?.let { "\"$it\"" } ?: "null"},
        |    "dueAt": ${event.dueAt?.let { "\"$it\"" } ?: "null"},
        |    "location": ${event.location?.let { "\"${it.escapeJson()}\"" } ?: "null"},
        |    "recurrenceRule": ${event.recurrenceRule?.let { "\"${it.escapeJson()}\"" } ?: "null"},
        |    "priority": ${event.priority?.let { "\"${it.name}\"" } ?: "null"},
        |    "notificationStatus": "PENDING",
        |    "syncStatus": "LOCAL_ONLY",
        |    "externalCalendarEventId": null,
        |    "timeZoneId": ${event.timeZoneId?.let { "\"${it.escapeJson()}\"" } ?: "null"},
        |    "creationKey": ${event.creationKey?.let { "\"${it.escapeJson()}\"" } ?: "null"}
        |  }
        """.trimMargin()
    }

fun exportEventsIcs(
    events: List<CalendarEvent>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
): String {
    val lines = mutableListOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Shengli//Voice Calendar//CN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
    )

    events.sortedBy { it.startAt }.forEach { event ->
        lines += "BEGIN:VEVENT"
        lines += "UID:${event.id.escapeIcs()}@shengli.local"
        lines += "DTSTAMP:${IcsDateTimeFormatter.format(now)}"
        if (event.allDay) {
            val startDate = event.startAt.atZone(zoneId).toLocalDate()
            val rawEndDate = event.endAt?.atZone(zoneId)?.toLocalDate()
            val endDate = rawEndDate?.takeIf { it.isAfter(startDate) } ?: startDate.plusDays(1)
            lines += "DTSTART;VALUE=DATE:${startDate.format(IcsDateFormatter)}"
            lines += "DTEND;VALUE=DATE:${endDate.format(IcsDateFormatter)}"
        } else {
            lines += "DTSTART:${IcsDateTimeFormatter.format(event.startAt)}"
            event.endAt?.let { lines += "DTEND:${IcsDateTimeFormatter.format(it)}" }
        }
        lines += "SUMMARY:${event.title.escapeIcs()}"
        event.location?.takeIf(String::isNotBlank)?.let { lines += "LOCATION:${it.escapeIcs()}" }
        event.descriptionForExport()?.let { lines += "DESCRIPTION:${it.escapeIcs()}" }
        event.recurrenceRule?.takeIf(String::isNotBlank)?.let { lines += "RRULE:${it.sanitizeIcsRule()}" }
        lines += "STATUS:CONFIRMED"
        lines += "X-SHENGLI-STATUS:${event.status.name}"
        lines += "X-SHENGLI-TYPE:${event.itemType.name}"
        event.priority?.let { lines += "X-SHENGLI-PRIORITY:${it.name}" }
        event.reminderMinutes.distinct().sorted().forEach { minutes ->
            lines += "BEGIN:VALARM"
            lines += "ACTION:DISPLAY"
            lines += "DESCRIPTION:${event.title.escapeIcs()}"
            lines += "TRIGGER:${minutes.toIcsTrigger()}"
            lines += "END:VALARM"
        }
        lines += "END:VEVENT"
    }

    lines += "END:VCALENDAR"
    return lines.joinToString(separator = "\r\n", postfix = "\r\n") { it.foldIcsLine() }
}

private fun CalendarEvent.descriptionForExport(): String? =
    listOfNotNull(
        notes?.takeIf { it.isNotBlank() },
        sourceTranscript?.takeIf { it.isNotBlank() }?.let { "\u539f\u6587: $it" },
    ).joinToString("\n").takeIf { it.isNotBlank() }

private fun Int.toIcsTrigger(): String =
    if (this <= 0) "PT0M" else "-PT${this}M"

private fun String.escapeJson(): String =
    buildString {
        this@escapeJson.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

private fun String.escapeIcs(): String =
    replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")

private fun String.sanitizeIcsRule(): String = replace("\r", "").replace("\n", "")

private fun String.foldIcsLine(): String =
    if (length <= IcsFoldLimit) {
        this
    } else {
        chunked(IcsFoldLimit).mapIndexed { index, chunk ->
            if (index == 0) chunk else " $chunk"
        }.joinToString("\r\n")
    }

private val IcsDateFormatter: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
private val IcsDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
private const val IcsFoldLimit = 74
