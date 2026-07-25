package com.voicememos.aicalendar.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRiskDetectorTest {
    private val displayZone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun eventConflictsIncludeOnlyOverlappingUncancelledEvents() {
        val draft = draft(
            itemType = ScheduleItemType.EVENT,
            startAt = instant("2026-07-25T02:00:00Z"),
            endAt = instant("2026-07-25T03:00:00Z"),
        )
        val existingEvents = listOf(
            event(
                id = "done-overlap",
                title = "已完成但未取消的会议",
                startAt = instant("2026-07-25T01:45:00Z"),
                endAt = instant("2026-07-25T02:15:00Z"),
                status = EventStatus.DONE,
            ),
            event(
                id = "scheduled-overlap",
                title = "团队会议",
                startAt = instant("2026-07-25T02:30:00Z"),
                endAt = instant("2026-07-25T03:30:00Z"),
            ),
            event(
                id = "archived-overlap",
                title = "已归档会议",
                startAt = instant("2026-07-25T02:15:00Z"),
                endAt = instant("2026-07-25T02:45:00Z"),
                status = EventStatus.ARCHIVED,
            ),
            event(
                id = "reminder-overlap",
                title = "提醒",
                startAt = instant("2026-07-25T02:15:00Z"),
                endAt = instant("2026-07-25T02:45:00Z"),
                itemType = ScheduleItemType.REMINDER,
            ),
        )

        val conflicts = ScheduleRiskDetector.detectEventConflicts(
            draft = draft,
            existingEvents = existingEvents,
            displayZone = displayZone,
        )

        assertEquals(listOf("done-overlap", "scheduled-overlap"), conflicts.map { it.conflictingEventId })
        assertEquals(instant("2026-07-25T02:00:00Z"), conflicts.first().overlapStartAt)
        assertEquals(instant("2026-07-25T02:15:00Z"), conflicts.first().overlapEndAt)
        assertTrue(conflicts.last().summary.contains("团队会议"))
        assertTrue(conflicts.last().summary.contains("2026-07-25 10:30"))
    }

    @Test
    fun touchingEventBoundariesDoNotConflict() {
        val draft = draft(
            itemType = ScheduleItemType.EVENT,
            startAt = instant("2026-07-25T02:00:00Z"),
            endAt = instant("2026-07-25T03:00:00Z"),
        )
        val adjacentEvents = listOf(
            event(
                id = "ends-at-start",
                startAt = instant("2026-07-25T01:00:00Z"),
                endAt = instant("2026-07-25T02:00:00Z"),
            ),
            event(
                id = "starts-at-end",
                startAt = instant("2026-07-25T03:00:00Z"),
                endAt = instant("2026-07-25T04:00:00Z"),
            ),
        )

        val conflicts = ScheduleRiskDetector.detectEventConflicts(
            draft = draft,
            existingEvents = adjacentEvents,
            displayZone = displayZone,
        )

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun remindersAndTodosNeverCreateOccupancyConflicts() {
        val occupiedEvent = event(
            id = "occupied",
            startAt = instant("2026-07-25T02:00:00Z"),
            endAt = instant("2026-07-25T03:00:00Z"),
        )
        val reminder = draft(
            itemType = ScheduleItemType.REMINDER,
            startAt = instant("2026-07-25T02:15:00Z"),
            endAt = instant("2026-07-25T02:45:00Z"),
            triggerAt = instant("2026-07-25T02:15:00Z"),
        )
        val todo = draft(
            itemType = ScheduleItemType.TODO,
            startAt = instant("2026-07-25T02:15:00Z"),
            endAt = instant("2026-07-25T02:45:00Z"),
            dueAt = instant("2026-07-25T02:45:00Z"),
        )

        assertTrue(
            ScheduleRiskDetector.detectEventConflicts(
                reminder,
                listOf(occupiedEvent),
                displayZone,
            ).isEmpty(),
        )
        assertTrue(
            ScheduleRiskDetector.detectEventConflicts(
                todo,
                listOf(occupiedEvent),
                displayZone,
            ).isEmpty(),
        )
    }

    @Test
    fun invalidOrMissingEventIntervalsDoNotOccupyTime() {
        val validDraft = draft(
            itemType = ScheduleItemType.EVENT,
            startAt = instant("2026-07-25T02:00:00Z"),
            endAt = instant("2026-07-25T03:00:00Z"),
        )
        val invalidExisting = listOf(
            event(
                id = "missing-end",
                startAt = instant("2026-07-25T02:15:00Z"),
                endAt = null,
            ),
            event(
                id = "reversed",
                startAt = instant("2026-07-25T02:45:00Z"),
                endAt = instant("2026-07-25T02:30:00Z"),
            ),
        )

        assertTrue(
            ScheduleRiskDetector.detectEventConflicts(
                validDraft,
                invalidExisting,
                displayZone,
            ).isEmpty(),
        )
        assertTrue(
            ScheduleRiskDetector.detectEventConflicts(
                draft(
                    itemType = ScheduleItemType.EVENT,
                    startAt = instant("2026-07-25T02:00:00Z"),
                    endAt = null,
                ),
                listOf(event(id = "valid")),
                displayZone,
            ).isEmpty(),
        )
    }

    @Test
    fun pastDetectionUsesTypeSpecificScheduleTime() {
        val now = instant("2026-07-25T04:00:00Z")
        val eventResult = ScheduleRiskDetector.detectPastTimes(
            draft = draft(
                itemType = ScheduleItemType.EVENT,
                startAt = instant("2026-07-25T03:00:00Z"),
                endAt = instant("2026-07-25T04:30:00Z"),
            ),
            now = now,
            displayZone = displayZone,
        )
        val reminderResult = ScheduleRiskDetector.detectPastTimes(
            draft = draft(
                itemType = ScheduleItemType.REMINDER,
                startAt = instant("2026-07-25T06:00:00Z"),
                triggerAt = instant("2026-07-25T03:30:00Z"),
            ),
            now = now,
            displayZone = displayZone,
        )
        val todoResult = ScheduleRiskDetector.detectPastTimes(
            draft = draft(
                itemType = ScheduleItemType.TODO,
                startAt = instant("2026-07-25T06:00:00Z"),
                dueAt = instant("2026-07-25T03:45:00Z"),
            ),
            now = now,
            displayZone = displayZone,
        )

        assertEquals(ScheduleTimeField.START, eventResult.single().field)
        assertEquals(ScheduleTimeField.TRIGGER, reminderResult.single().field)
        assertEquals(ScheduleTimeField.DUE, todoResult.single().field)
        assertTrue(eventResult.single().summary.contains("开始时间已过去"))
        assertTrue(reminderResult.single().summary.contains("提醒时间已过去"))
        assertTrue(todoResult.single().summary.contains("截止时间已过去"))
    }

    @Test
    fun timeEqualToNowOrInFutureIsNotPast() {
        val now = instant("2026-07-25T04:00:00Z")
        val equalToNow = draft(
            itemType = ScheduleItemType.REMINDER,
            triggerAt = now,
        )
        val future = draft(
            itemType = ScheduleItemType.TODO,
            dueAt = instant("2026-07-25T04:00:01Z"),
        )

        assertTrue(ScheduleRiskDetector.detectPastTimes(equalToNow, now, displayZone).isEmpty())
        assertTrue(ScheduleRiskDetector.detectPastTimes(future, now, displayZone).isEmpty())
    }

    @Test
    fun assessmentExposesReadableConflictAndPastFlags() {
        val draft = draft(
            title = "准备评审",
            itemType = ScheduleItemType.EVENT,
            startAt = instant("2026-07-25T02:00:00Z"),
            endAt = instant("2026-07-25T03:00:00Z"),
        )
        val assessment = ScheduleRiskDetector.assess(
            draft = draft,
            existingEvents = listOf(
                event(
                    id = "conflict",
                    title = "已有评审",
                    startAt = instant("2026-07-25T02:30:00Z"),
                    endAt = instant("2026-07-25T03:30:00Z"),
                ),
            ),
            now = instant("2026-07-25T02:15:00Z"),
            displayZone = displayZone,
        )

        assertTrue(assessment.hasEventConflicts)
        assertTrue(assessment.hasPastStartTime)
        assertFalse(assessment.hasPastTriggerTime)
        assertFalse(assessment.hasPastDueTime)
        assertTrue(assessment.eventConflicts.single().summary.contains("已有评审"))
    }

    private fun draft(
        title: String = "新事项",
        itemType: ScheduleItemType,
        startAt: Instant? = null,
        endAt: Instant? = null,
        triggerAt: Instant? = null,
        dueAt: Instant? = null,
    ): DraftCalendarEvent =
        DraftCalendarEvent(
            title = title,
            notes = null,
            startAt = startAt,
            endAt = endAt,
            allDay = false,
            reminderMinutes = listOf(0),
            sourceTranscript = title,
            aiConfidence = 1.0,
            missingFields = emptyList(),
            itemType = itemType,
            triggerAt = triggerAt,
            dueAt = dueAt,
        )

    private fun event(
        id: String,
        title: String = "已有事件",
        startAt: Instant = instant("2026-07-25T02:15:00Z"),
        endAt: Instant? = instant("2026-07-25T02:45:00Z"),
        status: EventStatus = EventStatus.SCHEDULED,
        itemType: ScheduleItemType = ScheduleItemType.EVENT,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            notes = null,
            startAt = startAt,
            endAt = endAt,
            allDay = false,
            reminderMinutes = listOf(0),
            status = status,
            sourceTranscript = null,
            aiConfidence = 1.0,
            itemType = itemType,
        )

    private fun instant(value: String): Instant = Instant.parse(value)
}
