package com.voicememos.aicalendar.domain

import java.time.Duration

fun DraftCalendarEvent.expandOccurrences(): List<DraftCalendarEvent> {
    val repeat = repeatRule ?: return listOf(this)
    return (0 until repeat.normalizedCount).map { index ->
        val offset = when (repeat.frequency) {
            RepeatFrequency.DAILY -> Duration.ofDays(index.toLong())
            RepeatFrequency.WEEKLY -> Duration.ofDays(index.toLong() * 7)
        }
        copy(
            startAt = startAt?.plus(offset),
            endAt = endAt?.plus(offset),
            triggerAt = triggerAt?.plus(offset),
            dueAt = dueAt?.plus(offset),
            notes = listOfNotNull(notes, repeat.summary())
                .joinToString("\n")
                .takeIf(String::isNotBlank),
        )
    }
}
