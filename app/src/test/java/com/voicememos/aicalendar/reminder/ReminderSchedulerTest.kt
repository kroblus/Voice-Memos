package com.voicememos.aicalendar.reminder

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSchedulerTest {
    private val now = Instant.parse("2026-07-25T08:00:00Z")

    @Test
    fun eventUsesStartAtAsReminderAnchor() {
        val startAt = now.plusSeconds(3_600)
        val event = event(
            itemType = ScheduleItemType.EVENT,
            startAt = startAt,
            triggerAt = now.plusSeconds(7_200),
            dueAt = now.plusSeconds(10_800),
        )

        assertEquals(startAt, event.reminderAnchor())
    }

    @Test
    fun reminderUsesTriggerAtAsReminderAnchor() {
        val triggerAt = now.plusSeconds(7_200)
        val reminder = event(
            itemType = ScheduleItemType.REMINDER,
            startAt = now.plusSeconds(3_600),
            triggerAt = triggerAt,
            dueAt = now.plusSeconds(10_800),
        )

        assertEquals(triggerAt, reminder.reminderAnchor())
    }

    @Test
    fun todoUsesDueAtAsReminderAnchor() {
        val dueAt = now.plusSeconds(10_800)
        val todo = event(
            itemType = ScheduleItemType.TODO,
            startAt = now.plusSeconds(3_600),
            triggerAt = now.plusSeconds(7_200),
            dueAt = dueAt,
        )

        assertEquals(dueAt, todo.reminderAnchor())
    }

    @Test
    fun todoWithoutDueDoesNotRequireScheduling() {
        val todo = event(
            itemType = ScheduleItemType.TODO,
            startAt = now.plusSeconds(3_600),
            dueAt = null,
        )

        assertNull(todo.reminderAnchor())
        assertEquals(
            ScheduleResult.NOT_REQUIRED,
            determineScheduleResult(
                triggerAt = todo.reminderAnchor(),
                now = now,
                canScheduleExact = true,
            ),
        )
    }

    @Test
    fun futureReminderUsesExactAlarmWhenCapabilityIsAvailable() {
        assertEquals(
            ScheduleResult.EXACT,
            determineScheduleResult(
                triggerAt = now.plusSeconds(60),
                now = now,
                canScheduleExact = true,
            ),
        )
    }

    @Test
    fun futureReminderFallsBackToInexactAlarmWithoutCapability() {
        assertEquals(
            ScheduleResult.INEXACT,
            determineScheduleResult(
                triggerAt = now.plusSeconds(60),
                now = now,
                canScheduleExact = false,
            ),
        )
    }

    @Test
    fun reminderAtCurrentInstantIsSkipped() {
        assertEquals(
            ScheduleResult.SKIPPED_PAST,
            determineScheduleResult(
                triggerAt = now,
                now = now,
                canScheduleExact = true,
            ),
        )
    }

    @Test
    fun reminderBeforeCurrentInstantIsSkipped() {
        val reminder = event(
            itemType = ScheduleItemType.REMINDER,
            triggerAt = now.minusSeconds(1),
        )

        assertEquals(
            ScheduleResult.SKIPPED_PAST,
            determineScheduleResult(
                triggerAt = reminder.reminderAnchor(),
                now = now,
                canScheduleExact = false,
            ),
        )
    }

    private fun event(
        itemType: ScheduleItemType,
        startAt: Instant = now.plusSeconds(3_600),
        triggerAt: Instant? = null,
        dueAt: Instant? = null,
    ): CalendarEvent =
        CalendarEvent(
            id = "event-${itemType.name.lowercase()}",
            title = "测试事项",
            notes = null,
            startAt = startAt,
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(0, 10),
            status = EventStatus.SCHEDULED,
            sourceTranscript = null,
            aiConfidence = 1.0,
            itemType = itemType,
            triggerAt = triggerAt,
            dueAt = dueAt,
        )
}
