package com.voicememos.aicalendar.calendar

import android.provider.CalendarContract.Events
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMirrorPayloadTest {
    @Test
    fun eventKeepsZeroAndFourNearestFutureReminderOffsets() {
        val event = event(
            startAt = Instant.parse("2026-07-27T04:00:00Z"),
            reminderMinutes = listOf(180, 90, 60, 30, 10, 0),
        )

        val payload = event.toCalendarMirrorPayload(
            now = Instant.parse("2026-07-27T02:00:00Z"),
            fallbackZoneId = ZoneId.of("Asia/Shanghai"),
        )

        requireNotNull(payload)
        assertEquals(listOf(0, 10, 30, 60, 90), payload.reminderMinutes)
        assertEquals(Events.AVAILABILITY_BUSY, payload.availability)
        assertEquals("shengli://item/event/event-1", payload.customAppUri)
        assertFalse(payload.allDay)
    }

    @Test
    fun reminderUsesOneMinuteFreeMarker() {
        val trigger = Instant.parse("2026-07-27T04:00:00Z")
        val payload = event(
            startAt = trigger,
            itemType = ScheduleItemType.REMINDER,
            triggerAt = trigger,
        ).toCalendarMirrorPayload(now = Instant.parse("2026-07-27T03:00:00Z"))

        requireNotNull(payload)
        assertEquals(60_000L, payload.endAtEpochMillis - payload.startAtEpochMillis)
        assertEquals(Events.AVAILABILITY_FREE, payload.availability)
    }

    @Test
    fun todoWithoutDueDoesNotCreateCalendarEvent() {
        val payload = event(
            itemType = ScheduleItemType.TODO,
            dueAt = null,
        ).toCalendarMirrorPayload(now = Instant.parse("2026-07-27T03:00:00Z"))

        assertNull(payload)
    }

    @Test
    fun allDayEventUsesUtcMidnightExclusiveEnd() {
        val payload = event(
            startAt = Instant.parse("2026-07-26T16:00:00Z"),
            endAt = Instant.parse("2026-07-27T16:00:00Z"),
            allDay = true,
            timeZoneId = "Asia/Shanghai",
        ).toCalendarMirrorPayload(now = Instant.parse("2026-07-26T00:00:00Z"))

        requireNotNull(payload)
        assertTrue(payload.allDay)
        assertEquals(Instant.parse("2026-07-27T00:00:00Z").toEpochMilli(), payload.startAtEpochMillis)
        assertEquals(Instant.parse("2026-07-28T00:00:00Z").toEpochMilli(), payload.endAtEpochMillis)
        assertEquals("Z", payload.timeZoneId)
    }

    private fun event(
        startAt: Instant = Instant.parse("2026-07-27T04:00:00Z"),
        endAt: Instant? = null,
        allDay: Boolean = false,
        reminderMinutes: List<Int> = listOf(0),
        itemType: ScheduleItemType = ScheduleItemType.EVENT,
        triggerAt: Instant? = null,
        dueAt: Instant? = null,
        timeZoneId: String? = "Asia/Shanghai",
    ): CalendarEvent =
        CalendarEvent(
            id = "event-1",
            title = "测试日程",
            notes = "只同步备注",
            startAt = startAt,
            endAt = endAt,
            allDay = allDay,
            reminderMinutes = reminderMinutes,
            status = EventStatus.SCHEDULED,
            sourceTranscript = "不应同步的原始转写",
            aiConfidence = 1.0,
            itemType = itemType,
            triggerAt = triggerAt,
            dueAt = dueAt,
            timeZoneId = timeZoneId,
        )
}
