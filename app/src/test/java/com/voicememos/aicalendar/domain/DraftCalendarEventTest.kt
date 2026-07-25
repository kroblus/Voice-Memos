package com.voicememos.aicalendar.domain

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftCalendarEventTest {
    @Test
    fun todoWithoutDueIsCompleteAndCreatable() {
        val draft = draft(
            itemType = ScheduleItemType.TODO,
            startAt = null,
            dueAt = null,
        )

        assertTrue(draft.isSemanticallyComplete)
        assertTrue(draft.canCreate)
    }

    @Test
    fun todoStillRequiresTitleAndResolvedFields() {
        assertFalse(
            draft(
                itemType = ScheduleItemType.TODO,
                title = " ",
            ).isSemanticallyComplete,
        )
        assertFalse(
            draft(
                itemType = ScheduleItemType.TODO,
                missingFields = listOf("title"),
            ).canCreate,
        )
        assertFalse(
            draft(
                itemType = ScheduleItemType.TODO,
                ambiguousFields = listOf("title"),
            ).canCreate,
        )
    }

    @Test
    fun eventAndReminderCreationRulesRemainUnchanged() {
        val triggerAt = Instant.parse("2026-07-26T02:00:00Z")

        assertFalse(
            draft(
                itemType = ScheduleItemType.EVENT,
                startAt = null,
            ).canCreate,
        )
        assertFalse(
            draft(
                itemType = ScheduleItemType.REMINDER,
                startAt = null,
                triggerAt = triggerAt,
            ).canCreate,
        )
        assertTrue(
            draft(
                itemType = ScheduleItemType.REMINDER,
                startAt = triggerAt,
                triggerAt = triggerAt,
            ).canCreate,
        )
    }

    private fun draft(
        itemType: ScheduleItemType,
        title: String = "待处理事项",
        startAt: Instant? = null,
        triggerAt: Instant? = null,
        dueAt: Instant? = null,
        missingFields: List<String> = emptyList(),
        ambiguousFields: List<String> = emptyList(),
    ): DraftCalendarEvent =
        DraftCalendarEvent(
            title = title,
            notes = null,
            startAt = startAt,
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(0),
            sourceTranscript = title,
            aiConfidence = 1.0,
            missingFields = missingFields,
            itemType = itemType,
            triggerAt = triggerAt,
            dueAt = dueAt,
            ambiguousFields = ambiguousFields,
        )
}
