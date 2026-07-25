package com.voicememos.aicalendar.reminder

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {
    private val now = Instant.parse("2026-07-25T08:00:00Z")

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
        assertEquals(
            ScheduleResult.SKIPPED_PAST,
            determineScheduleResult(
                triggerAt = now.minusSeconds(1),
                now = now,
                canScheduleExact = false,
            ),
        )
    }
}
