package com.voicememos.aicalendar.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftRecurrenceTest {
    @Test
    fun `event recurrence shifts start and end`() {
        val draft = draft(
            type = ScheduleItemType.EVENT,
            start = Instant.parse("2026-07-26T02:00:00Z"),
            end = Instant.parse("2026-07-26T03:00:00Z"),
            repeat = RepeatRule(RepeatFrequency.DAILY, 3),
        )

        val occurrences = draft.expandOccurrences()

        assertEquals(3, occurrences.size)
        assertEquals(Instant.parse("2026-07-28T02:00:00Z"), occurrences.last().startAt)
        assertEquals(Instant.parse("2026-07-28T03:00:00Z"), occurrences.last().endAt)
    }

    @Test
    fun `reminder recurrence shifts compatibility and trigger anchors`() {
        val trigger = Instant.parse("2026-07-26T13:00:00Z")
        val draft = draft(
            type = ScheduleItemType.REMINDER,
            start = trigger,
            trigger = trigger,
            repeat = RepeatRule(RepeatFrequency.WEEKLY, 2),
        )

        val second = draft.expandOccurrences().last()

        assertEquals(Instant.parse("2026-08-02T13:00:00Z"), second.startAt)
        assertEquals(Instant.parse("2026-08-02T13:00:00Z"), second.triggerAt)
    }

    @Test
    fun `undated todo remains undated and receives no synthetic recurrence anchor`() {
        val draft = draft(
            type = ScheduleItemType.TODO,
            start = null,
            repeat = RepeatRule(RepeatFrequency.DAILY, 2),
        )

        val occurrences = draft.expandOccurrences()

        assertEquals(2, occurrences.size)
        assertNull(occurrences.last().startAt)
        assertNull(occurrences.last().dueAt)
    }

    private fun draft(
        type: ScheduleItemType,
        start: Instant?,
        end: Instant? = null,
        trigger: Instant? = null,
        repeat: RepeatRule,
    ): DraftCalendarEvent =
        DraftCalendarEvent(
            title = "测试",
            notes = null,
            startAt = start,
            endAt = end,
            allDay = false,
            reminderMinutes = listOf(0),
            sourceTranscript = "测试",
            aiConfidence = 1.0,
            missingFields = emptyList(),
            repeatRule = repeat,
            itemType = type,
            triggerAt = trigger,
        )
}
