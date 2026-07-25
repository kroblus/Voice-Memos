package com.voicememos.aicalendar.ai

import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseMapperTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun mapsMultipleEventsWithDefaultDurationAndReminder() {
        val result = AiResponseMapper.map(
            response = AiCalendarResponse(
                events = listOf(
                    AiCalendarEvent(
                        title = "给张三打电话",
                        startLocal = "2026-06-17T09:00:00",
                        confidence = 0.92,
                    ),
                    AiCalendarEvent(
                        title = "项目会",
                        startLocal = "2026-06-17T15:00:00",
                        endLocal = "2026-06-17T16:00:00",
                        reminderMinutes = listOf(20, 10, 20),
                        confidence = 0.9,
                    ),
                ),
            ),
            transcript = "明天上午九点提醒我给张三打电话，下午三点开项目会",
            zoneId = zone,
            defaultDurationMinutes = 30,
            defaultReminderMinutes = 10,
        )

        assertEquals(2, result.events.size)
        assertEquals(listOf(0, 10), result.events[0].reminderMinutes)
        assertEquals(listOf(0, 10, 20), result.events[1].reminderMinutes)
        assertTrue(result.events.all { it.canCreate })
        assertTrue(result.needsConfirmation)
    }

    @Test
    fun marksMissingStartTimeAsIncomplete() {
        val result = AiResponseMapper.map(
            response = AiCalendarResponse(
                events = listOf(
                    AiCalendarEvent(
                        title = "买菜",
                        startLocal = null,
                        confidence = 0.58,
                    ),
                ),
            ),
            transcript = "周末买菜",
            zoneId = zone,
            defaultDurationMinutes = 30,
            defaultReminderMinutes = 10,
        )

        assertFalse(result.events.single().canCreate)
        assertEquals(listOf("start_local"), result.events.single().missingFields)
        assertTrue(result.needsConfirmation)
    }

    @Test
    fun mapsRepeatRuleWhenAiReturnsRecurringFields() {
        val result = AiResponseMapper.map(
            response = AiCalendarResponse(
                events = listOf(
                    AiCalendarEvent(
                        title = "喝水",
                        startLocal = "2026-06-17T09:00:00",
                        repeatFrequency = "daily",
                        repeatCount = 5,
                        confidence = 0.9,
                    ),
                ),
            ),
            transcript = "每天九点提醒我喝水",
            zoneId = zone,
            defaultDurationMinutes = 30,
            defaultReminderMinutes = 10,
        )

        val draft = result.events.single()
        assertEquals("喝水", draft.title)
        assertEquals("每天，共 5 次", draft.repeatRule?.summary())
        assertEquals(listOf(0, 10), draft.reminderMinutes)
        assertTrue(draft.canCreate)
    }

    @Test
    fun mapsTypedEventReminderAndTodoItems() {
        val result = AiResponseMapper.map(
            response = AiCalendarResponse(
                items = listOf(
                    AiCalendarEvent(
                        type = "EVENT",
                        title = "项目会",
                        start = "2026-06-17T15:00:00",
                        end = "2026-06-17T16:00:00",
                        timeZone = "Asia/Shanghai",
                        location = "会议室 A",
                        recurrenceRule = "FREQ=WEEKLY;COUNT=4",
                        reminderOffsets = listOf(30),
                        confidence = 0.93,
                    ),
                    AiCalendarEvent(
                        type = "REMINDER",
                        title = "吃药",
                        trigger = "2026-06-17T21:00:00",
                        timeZone = "Asia/Shanghai",
                        reminderOffsets = listOf(0),
                        confidence = 0.95,
                    ),
                    AiCalendarEvent(
                        type = "TODO",
                        title = "交作业",
                        due = "2026-06-18",
                        timeZone = "Asia/Shanghai",
                        priority = "HIGH",
                        confidence = 0.9,
                    ),
                ),
            ),
            transcript = "明天下午三点开项目会，晚上九点提醒我吃药，后天交作业",
            zoneId = zone,
            defaultDurationMinutes = 30,
            defaultReminderMinutes = 10,
        )

        assertEquals(3, result.items.size)
        val event = result.items[0]
        assertEquals(ScheduleItemType.EVENT, event.itemType)
        assertEquals("会议室 A", event.location)
        assertEquals("FREQ=WEEKLY;COUNT=4", event.recurrenceRule)
        assertEquals("每周，共 4 次", event.repeatRule?.summary())
        assertEquals(listOf(0, 30), event.reminderOffsets)

        val reminder = result.items[1]
        assertEquals(ScheduleItemType.REMINDER, reminder.itemType)
        assertNotNull(reminder.triggerAt)
        assertEquals(reminder.triggerAt, reminder.startAt)
        assertTrue(reminder.canCreate)

        val todo = result.items[2]
        assertEquals(ScheduleItemType.TODO, todo.itemType)
        assertEquals(ScheduleItemPriority.HIGH, todo.priority)
        assertNotNull(todo.dueAt)
        assertTrue(todo.allDay)
        assertTrue(todo.canCreate)
    }

    @Test
    fun keepsValidItemsAndTurnsInvalidItemsIntoWarnings() {
        val result = AiResponseMapper.map(
            response = AiCalendarResponse(
                items = listOf(
                    AiCalendarEvent(
                        type = "TODO",
                        title = "有效待办",
                        due = "2026-06-18T18:00:00",
                        timeZone = "Asia/Shanghai",
                        priority = "NORMAL",
                        confidence = 0.8,
                    ),
                    AiCalendarEvent(
                        type = "UNKNOWN",
                        title = "未知类型",
                        start = "2026-06-18T09:00:00",
                        confidence = 0.8,
                    ),
                    AiCalendarEvent(
                        type = "EVENT",
                        title = "时间倒置",
                        start = "2026-06-18T10:00:00",
                        end = "2026-06-18T09:00:00",
                        confidence = 0.8,
                    ),
                    AiCalendarEvent(
                        type = "EVENT",
                        title = "错误时区",
                        start = "2026-06-18T09:00:00",
                        timeZone = "Mars/Olympus",
                        confidence = 0.8,
                    ),
                ),
            ),
            transcript = "测试非法结果",
            zoneId = zone,
            defaultDurationMinutes = 30,
            defaultReminderMinutes = 10,
        )

        assertEquals(listOf("有效待办"), result.items.map { it.title })
        assertTrue(result.warnings.any { it.contains("未知 type") })
        assertTrue(result.warnings.any { it.contains("end 必须晚于 start") })
        assertTrue(result.warnings.any { it.contains("IANA 时区") })
    }

    @Test
    fun requiresClarificationForAmbiguousKeyTime() {
        val result = AiResponseMapper.map(
            response = AiCalendarResponse(
                items = listOf(
                    AiCalendarEvent(
                        type = "REMINDER",
                        title = "联系导师",
                        trigger = "2026-06-18T18:00:00",
                        timeZone = "Asia/Shanghai",
                        confidence = 0.55,
                        ambiguousFields = listOf("trigger"),
                    ),
                ),
                requiresClarification = true,
                clarificationQuestions = listOf("“晚点”是指 18:00 吗？"),
            ),
            transcript = "晚点提醒我联系导师",
            zoneId = zone,
            defaultDurationMinutes = 30,
            defaultReminderMinutes = 10,
        )

        val reminder = result.items.single()
        assertTrue(result.requiresClarification)
        assertEquals(listOf("“晚点”是指 18:00 吗？"), result.clarificationQuestions)
        assertEquals(listOf("trigger"), reminder.ambiguousFields)
        assertTrue("trigger" in reminder.missingFields)
        assertFalse(reminder.canCreate)
    }

    @Test
    fun rejectsOffsetThatDoesNotMatchDeclaredTimeZone() {
        val result = AiResponseMapper.map(
            response = AiCalendarResponse(
                items = listOf(
                    AiCalendarEvent(
                        type = "EVENT",
                        title = "偏移错误",
                        start = "2026-06-18T09:00:00Z",
                        timeZone = "Asia/Shanghai",
                        confidence = 0.8,
                    ),
                ),
            ),
            transcript = "测试时区",
            zoneId = zone,
            defaultDurationMinutes = 30,
            defaultReminderMinutes = 10,
        )

        assertTrue(result.items.isEmpty())
        assertTrue(result.warnings.single().contains("UTC 偏移"))
    }
}
