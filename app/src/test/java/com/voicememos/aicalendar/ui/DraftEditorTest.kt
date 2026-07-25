package com.voicememos.aicalendar.ui

import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import com.voicememos.aicalendar.domain.RepeatFrequency
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftEditorTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun reminderConversionPreservesTypedFields() {
        val draft = editor(
            itemType = ScheduleItemType.REMINDER,
            triggerLocal = "2026-07-26 09:30",
            location = "一号会议室",
            recurrenceRule = "FREQ=WEEKLY;COUNT=4",
            timeZoneId = zoneId.id,
        ).toDraft(ZoneId.of("UTC"))

        assertTrue(draft.canCreate)
        assertEquals(ScheduleItemType.REMINDER, draft.itemType)
        assertEquals(Instant.parse("2026-07-26T01:30:00Z"), draft.triggerAt)
        assertEquals(draft.triggerAt, draft.startAt)
        assertEquals("一号会议室", draft.location)
        assertEquals("FREQ=WEEKLY;COUNT=4", draft.recurrenceRule)
        assertEquals(zoneId.id, draft.timeZoneId)
        assertNull(draft.priority)
    }

    @Test
    fun ambiguityBlocksCreationUntilUserResolvesTheField() {
        val ambiguous = editor(
            startLocal = "2026-07-26 09:30",
            endLocal = "2026-07-26 10:30",
            missingFields = listOf("start"),
            ambiguousFields = listOf("startAt"),
        )

        assertFalse(ambiguous.canCreate(zoneId))
        assertEquals(listOf("开始时间"), ambiguous.blockingIssueLabels(zoneId))

        val resolved = ambiguous.resolveFields(
            "start",
            "start_local",
            "startAt",
            "startDateTime",
        )

        assertTrue(resolved.canCreate(zoneId))
        assertTrue(resolved.blockingIssueLabels(zoneId).isEmpty())
    }

    @Test
    fun switchingToTodoCopiesUsableTimeAndKeepsCompatibilityStart() {
        val todo = editor(
            startLocal = "2026-07-27 18:00",
            missingFields = listOf("item_type"),
            ambiguousFields = listOf("type"),
        ).withItemType(ScheduleItemType.TODO)

        val draft = todo.toDraft(zoneId)

        assertEquals("2026-07-27 18:00", todo.dueLocal)
        assertTrue(todo.ambiguousFields.isEmpty())
        assertTrue(draft.canCreate)
        assertEquals(ScheduleItemType.TODO, draft.itemType)
        assertEquals(draft.dueAt, draft.startAt)
        assertEquals(ScheduleItemPriority.NORMAL, draft.priority)
    }

    @Test
    fun todoWithoutDueIsCreatableFromEditor() {
        val editor = editor(
            itemType = ScheduleItemType.TODO,
            dueLocal = "",
        )

        val draft = editor.toDraft(zoneId)

        assertTrue(editor.canCreate(zoneId))
        assertTrue(draft.canCreate)
        assertNull(draft.startAt)
        assertNull(draft.dueAt)
    }

    @Test
    fun supportedRruleIsNormalizedBeforeDraftCreation() {
        val editor = editor(
            startLocal = "2026-07-26 09:30",
            endLocal = "2026-07-26 10:30",
            recurrenceRule = " freq = weekly ; count = 4 ",
        )

        val draft = editor.toDraft(zoneId)

        assertTrue(editor.canCreate(zoneId))
        assertEquals("FREQ=WEEKLY;COUNT=4", draft.recurrenceRule)
        assertEquals(RepeatFrequency.WEEKLY, draft.repeatRule?.frequency)
        assertEquals(4, draft.repeatRule?.normalizedCount)
        assertFalse("recurrence_rule" in draft.missingFields)
    }

    @Test
    fun invalidOrUnsupportedRruleBlocksDraftCreation() {
        val invalidRules = listOf(
            "FREQ=MONTHLY;COUNT=4",
            "FREQ=WEEKLY;INTERVAL=2",
            "FREQ=DAILY;COUNT=1",
            "FREQ=DAILY;COUNT=61",
            "FREQ=WEEKLY;COUNT=4;COUNT=5",
        )

        invalidRules.forEach { rule ->
            val editor = editor(
                startLocal = "2026-07-26 09:30",
                endLocal = "2026-07-26 10:30",
                recurrenceRule = rule,
            )
            val draft = editor.toDraft(zoneId)

            assertFalse("Rule should be blocked: $rule", editor.canCreate(zoneId))
            assertTrue("recurrence_rule" in draft.missingFields)
            assertNull(draft.recurrenceRule)
            assertNull(draft.repeatRule)
        }
    }

    @Test
    fun invalidEventEndProducesAVisibleBlockingIssue() {
        val editor = editor(
            startLocal = "2026-07-26 10:30",
            endLocal = "2026-07-26 09:30",
        )

        assertFalse(editor.canCreate(zoneId))
        assertEquals(listOf("结束时间"), editor.blockingIssueLabels(zoneId))
    }

    private fun editor(
        itemType: ScheduleItemType = ScheduleItemType.EVENT,
        startLocal: String = "",
        endLocal: String = "",
        triggerLocal: String = "",
        dueLocal: String = "",
        location: String = "",
        recurrenceRule: String = "",
        timeZoneId: String = zoneId.id,
        missingFields: List<String> = emptyList(),
        ambiguousFields: List<String> = emptyList(),
    ): DraftEditor =
        DraftEditor(
            draftId = "draft-1",
            title = "测试安排",
            notes = "带材料",
            startLocal = startLocal,
            endLocal = endLocal,
            allDay = false,
            reminderMinutes = "10",
            sourceTranscript = "测试语音",
            aiConfidence = 0.91,
            missingFields = missingFields,
            itemType = itemType,
            triggerLocal = triggerLocal,
            dueLocal = dueLocal,
            location = location,
            priority = ScheduleItemPriority.NORMAL,
            ambiguousFields = ambiguousFields,
            timeZoneId = timeZoneId,
            recurrenceRule = recurrenceRule,
        )
}
