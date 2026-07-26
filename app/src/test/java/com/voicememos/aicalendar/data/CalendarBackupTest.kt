package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.NotificationStatus
import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import com.voicememos.aicalendar.domain.SyncStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarBackupTest {
    @Test
    fun backupRoundTripsEventsWithoutApiKey() {
        val event = event(
            id = "event-1",
            title = "项目会",
            reminderMinutes = listOf(10),
            sourceTranscript = "明天九点项目会",
        )

        val backup = exportCalendarBackup(
            events = listOf(event),
            now = Instant.parse("2026-06-18T00:00:00Z"),
        )
        val decoded = decodeCalendarBackup(backup)

        assertTrue(backup.contains("\"schemaVersion\": 3"))
        assertTrue(backup.contains("\"exportedAt\": \"2026-06-18T00:00:00Z\""))
        assertFalse(backup.contains("deepseek"))
        assertFalse(backup.contains("apiKey"))
        assertEquals(1, decoded.events.size)
        assertEquals("项目会", decoded.events.single().title)
        assertEquals(listOf(0, 10), decoded.events.single().reminderMinutes)
    }

    @Test
    fun backupV3PreservesBusinessFieldsButResetsDeviceLocalDeliveryState() {
        val dueAt = Instant.parse("2026-07-31T09:00:00Z")
        val event = CalendarEvent(
            id = "todo-1",
            title = "提交申请",
            notes = "准备附件",
            startAt = dueAt,
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(0, 30),
            status = EventStatus.SCHEDULED,
            sourceTranscript = "周五前提交申请",
            aiConfidence = 0.88,
            itemType = ScheduleItemType.TODO,
            dueAt = dueAt,
            location = "办公室",
            recurrenceRule = "FREQ=WEEKLY;COUNT=2",
            priority = ScheduleItemPriority.HIGH,
            notificationStatus = NotificationStatus.SCHEDULED,
            syncStatus = SyncStatus.SYNCED,
            externalCalendarEventId = "external-7",
            creationKey = "session-7:item-2",
            timeZoneId = "Asia/Shanghai",
        )

        val backup = exportCalendarBackup(listOf(event))
        val restored = decodeCalendarBackup(backup).events.single()

        assertTrue(backup.contains("\"itemType\": \"TODO\""))
        assertTrue(backup.contains("\"creationKey\": \"session-7:item-2\""))
        assertEquals(event.copy(
            notificationStatus = NotificationStatus.PENDING,
            syncStatus = SyncStatus.LOCAL_ONLY,
            externalCalendarEventId = null,
        ), restored)
        assertFalse(backup.contains("external-7"))
    }

    @Test
    fun importsLegacyJsonArrayAndNormalizesReminders() {
        val legacy = """
            [
              {
                "id": "legacy-1",
                "title": "牙医",
                "notes": null,
                "startAt": "2026-06-19T07:00:00Z",
                "endAt": null,
                "allDay": false,
                "reminderMinutes": [20, 10, 20],
                "status": "SCHEDULED",
                "sourceTranscript": "周五三点牙医",
                "aiConfidence": 0.9
              }
            ]
        """.trimIndent()

        val decoded = decodeCalendarBackup(legacy)

        assertEquals(1, decoded.events.size)
        assertEquals(0, decoded.skippedCount)
        assertEquals(listOf(0, 10, 20), decoded.events.single().reminderMinutes)
        assertEquals(ScheduleItemType.EVENT, decoded.events.single().itemType)
        assertEquals(NotificationStatus.PENDING, decoded.events.single().notificationStatus)
        assertEquals(SyncStatus.LOCAL_ONLY, decoded.events.single().syncStatus)
    }

    @Test
    fun skipsInvalidBackupEvents() {
        val backup = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-06-18T00:00:00Z",
              "events": [
                {"id":"bad-1","title":"","startAt":"2026-06-19T07:00:00Z"},
                {"id":"bad-2","title":"无时间","startAt":""},
                {"id":"ok-1","title":"喝水","startAt":"2026-06-19T01:00:00Z"}
              ]
            }
        """.trimIndent()

        val decoded = decodeCalendarBackup(backup)

        assertEquals(1, decoded.events.size)
        assertEquals(2, decoded.skippedCount)
        assertEquals("喝水", decoded.events.single().title)
        assertEquals(listOf(0), decoded.events.single().reminderMinutes)
    }

    private fun event(
        id: String = "event",
        title: String = "任务",
        startAt: Instant = Instant.parse("2026-06-19T01:00:00Z"),
        reminderMinutes: List<Int> = listOf(0),
        sourceTranscript: String? = null,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            notes = null,
            startAt = startAt,
            endAt = null,
            allDay = false,
            reminderMinutes = reminderMinutes,
            status = EventStatus.SCHEDULED,
            sourceTranscript = sourceTranscript,
            aiConfidence = 0.9,
        )
}
