package com.voicememos.aicalendar.ai

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
