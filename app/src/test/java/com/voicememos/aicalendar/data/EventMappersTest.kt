package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.NotificationStatus
import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import com.voicememos.aicalendar.domain.SyncStatus
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

    @Test
    fun typedReminderRoundTripsEveryV2Field() {
        val triggerAt = Instant.parse("2026-07-26T13:00:00Z")
        val event = CalendarEvent(
            id = "reminder-1",
            title = "吃药",
            notes = "饭后",
            startAt = triggerAt,
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(0, 10),
            status = EventStatus.SCHEDULED,
            sourceTranscript = "晚上九点提醒我吃药",
            aiConfidence = 0.93,
            itemType = ScheduleItemType.REMINDER,
            triggerAt = triggerAt,
            dueAt = null,
            location = "家",
            recurrenceRule = "FREQ=DAILY;COUNT=7",
            priority = ScheduleItemPriority.HIGH,
            notificationStatus = NotificationStatus.SCHEDULED,
            syncStatus = SyncStatus.SYNCED,
            externalCalendarEventId = "calendar-42",
            creationKey = "session-1:item-1",
        )

        val restored = event.toEntity(
            now = Instant.parse("2026-07-25T00:00:00Z"),
        ).toDomain()

        assertEquals(event, restored)
    }

    @Test
    fun todoWithoutDueUsesCreationTimeOnlyAsLegacyStartAnchor() {
        val creationTime = Instant.parse("2026-07-25T05:00:00Z")
        val staleLegacyStart = Instant.parse("2026-07-20T05:00:00Z")
        val draft = DraftCalendarEvent(
            title = "整理书架",
            notes = null,
            startAt = staleLegacyStart,
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(0),
            sourceTranscript = "记一个待办：整理书架",
            aiConfidence = 0.96,
            missingFields = emptyList(),
            itemType = ScheduleItemType.TODO,
            dueAt = null,
        )

        val entity = draft.toEntity(now = creationTime)
        val restored = entity.toDomain()

        assertEquals(creationTime.toEpochMilli(), entity.startAtEpochMillis)
        assertEquals(creationTime.toEpochMilli(), entity.createdAtEpochMillis)
        assertNull(entity.dueAtEpochMillis)
        assertEquals(ScheduleItemType.TODO, restored.itemType)
        assertEquals(creationTime, restored.startAt)
        assertNull(restored.dueAt)
    }

    @Test
    fun todoWithDueKeepsDueAsPersistenceAnchor() {
        val creationTime = Instant.parse("2026-07-25T05:00:00Z")
        val dueAt = Instant.parse("2026-07-28T10:00:00Z")
        val draft = DraftCalendarEvent(
            title = "提交报告",
            notes = null,
            startAt = null,
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(0),
            sourceTranscript = "周二前提交报告",
            aiConfidence = 0.95,
            missingFields = emptyList(),
            itemType = ScheduleItemType.TODO,
            dueAt = dueAt,
        )

        val entity = draft.toEntity(now = creationTime)

        assertEquals(dueAt.toEpochMilli(), entity.startAtEpochMillis)
        assertEquals(dueAt.toEpochMilli(), entity.dueAtEpochMillis)
    }
}
