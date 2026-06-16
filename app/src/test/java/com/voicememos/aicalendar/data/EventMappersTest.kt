package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.DraftCalendarEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventMappersTest {
    @Test
    fun reminderCsvSortsAndDropsInvalidValues() {
        assertEquals("0,10,1440", listOf(10, -5, 0, 10, 1440).toReminderCsv())
        assertEquals("0,10", listOf(10).toReminderCsv())
        assertEquals(listOf(0, 10, 1440), "1440, junk, 10, 0, 10".toReminderMinutes())
    }

    @Test
    fun draftToEntityTrimsOptionalFields() {
        val start = Instant.parse("2026-06-16T01:00:00Z")
        val draft = DraftCalendarEvent(
            title = "  给张三打电话  ",
            notes = "   ",
            startAt = start,
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(10),
            sourceTranscript = "明天给张三打电话",
            aiConfidence = 0.91,
            missingFields = emptyList(),
        )

        val entity = draft.toEntity(now = Instant.parse("2026-06-15T00:00:00Z"))

        assertEquals("给张三打电话", entity.title)
        assertNull(entity.notes)
        assertEquals(start.toEpochMilli(), entity.startAtEpochMillis)
        assertEquals("0,10", entity.reminderMinutesCsv)
    }
}
