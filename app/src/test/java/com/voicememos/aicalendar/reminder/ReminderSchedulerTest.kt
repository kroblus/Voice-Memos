package com.voicememos.aicalendar.reminder

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.NotificationStatus
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSchedulerTest {
    private val now = Instant.parse("2026-07-25T08:00:00Z")

    @Test
    fun scheduleResultsMapToPersistedNotificationStatuses() {
        assertEquals(NotificationStatus.SCHEDULED, ScheduleResult.EXACT.toNotificationStatus())
        assertEquals(NotificationStatus.SCHEDULED, ScheduleResult.INEXACT.toNotificationStatus())
        assertEquals(NotificationStatus.FAILED, ScheduleResult.FAILED.toNotificationStatus())
        assertEquals(NotificationStatus.FAILED, ScheduleResult.SKIPPED_PAST.toNotificationStatus())
        assertEquals(
            NotificationStatus.NOT_REQUIRED,
            ScheduleResult.NOT_REQUIRED.toNotificationStatus(),
        )
    }

    @Test
    fun scheduleBatchAccountsForNotificationPermissionAndFailures() {
        assertEquals(
            NotificationStatus.SCHEDULED,
            mapOf(0 to ScheduleResult.EXACT, 10 to ScheduleResult.INEXACT)
                .toNotificationStatus(notificationAllowed = true),
        )
        assertEquals(
            NotificationStatus.FAILED,
            mapOf(0 to ScheduleResult.EXACT)
                .toNotificationStatus(notificationAllowed = false),
        )
        assertEquals(
            NotificationStatus.FAILED,
            mapOf(0 to ScheduleResult.EXACT, 10 to ScheduleResult.SKIPPED_PAST)
                .toNotificationStatus(notificationAllowed = true),
        )
        assertEquals(
            NotificationStatus.NOT_REQUIRED,
            emptyMap<Int, ScheduleResult>()
                .toNotificationStatus(notificationAllowed = true),
        )
    }

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
