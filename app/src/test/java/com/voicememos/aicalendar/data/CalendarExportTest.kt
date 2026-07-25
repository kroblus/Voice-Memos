package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarExportTest {
    @Test
    fun jsonExportEscapesTextFields() {
        val json = exportEventsJson(
            listOf(
                event(
                    title = "项目\"会",
                    notes = "A\\B\nC",
                    sourceTranscript = "明天开会",
                ),
            ),
        )

        assertTrue(json.contains("\"title\": \"项目\\\"会\""))
        assertTrue(json.contains("\"notes\": \"A\\\\B\\nC\""))
        assertTrue(json.contains("\"sourceTranscript\": \"明天开会\""))
    }

    @Test
    fun icsExportIncludesTimedEventRemindersAndStatus() {
        val ics = exportEventsIcs(
            events = listOf(
                event(
                    id = "meeting-1",
                    title = "项目会",
                    startAt = Instant.parse("2026-06-17T01:00:00Z"),
                    endAt = Instant.parse("2026-06-17T01:30:00Z"),
                    reminderMinutes = listOf(0, 10),
                    status = EventStatus.SCHEDULED,
                ),
            ),
            zoneId = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2026-06-16T00:00:00Z"),
        )

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("UID:meeting-1@shengli.local"))
        assertTrue(ics.contains("DTSTAMP:20260616T000000Z"))
        assertTrue(ics.contains("DTSTART:20260617T010000Z"))
        assertTrue(ics.contains("DTEND:20260617T013000Z"))
        assertTrue(ics.contains("SUMMARY:项目会"))
        assertTrue(ics.contains("TRIGGER:PT0M"))
        assertTrue(ics.contains("TRIGGER:-PT10M"))
        assertTrue(ics.contains("X-SHENGLI-STATUS:SCHEDULED"))
        assertTrue(ics.contains("X-SHENGLI-TYPE:EVENT"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun icsExportUsesDateValuesForAllDayEvents() {
        val ics = exportEventsIcs(
            events = listOf(
                event(
                    id = "allday-1",
                    title = "休息",
                    startAt = Instant.parse("2026-06-18T00:00:00Z"),
                    endAt = null,
                    allDay = true,
                ),
            ),
            zoneId = ZoneId.of("UTC"),
            now = Instant.parse("2026-06-16T00:00:00Z"),
        )

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260618"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260619"))
    }

    @Test
    fun typedTodoExportsTypeMetadataToJsonAndIcs() {
        val dueAt = Instant.parse("2026-07-31T09:00:00Z")
        val todo = event(
            id = "todo-1",
            title = "提交申请",
            startAt = dueAt,
        ).copy(
            itemType = ScheduleItemType.TODO,
            dueAt = dueAt,
            location = "办公室",
            recurrenceRule = "FREQ=WEEKLY;COUNT=2",
            priority = ScheduleItemPriority.HIGH,
            creationKey = "session-1:item-2",
        )

        val json = exportEventsJson(listOf(todo))
        val ics = exportEventsIcs(
            events = listOf(todo),
            zoneId = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2026-07-25T00:00:00Z"),
        )

        assertTrue(json.contains("\"itemType\": \"TODO\""))
        assertTrue(json.contains("\"dueAt\": \"2026-07-31T09:00:00Z\""))
        assertTrue(json.contains("\"creationKey\": \"session-1:item-2\""))
        assertTrue(ics.contains("X-SHENGLI-TYPE:TODO"))
        assertTrue(ics.contains("X-SHENGLI-PRIORITY:HIGH"))
        assertTrue(ics.contains("LOCATION:办公室"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;COUNT=2"))
    }

    private fun event(
        id: String = "event-1",
        title: String = "任务",
        notes: String? = null,
        startAt: Instant = Instant.parse("2026-06-17T01:00:00Z"),
        endAt: Instant? = null,
        allDay: Boolean = false,
        reminderMinutes: List<Int> = listOf(0),
        status: EventStatus = EventStatus.SCHEDULED,
        sourceTranscript: String? = null,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            notes = notes,
            startAt = startAt,
            endAt = endAt,
            allDay = allDay,
            reminderMinutes = reminderMinutes,
            status = status,
            sourceTranscript = sourceTranscript,
            aiConfidence = 0.9,
        )
}
