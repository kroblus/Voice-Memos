package com.voicememos.aicalendar.domain

import java.time.Instant

data class CalendarEvent(
    val id: String,
    val title: String,
    val notes: String?,
    val startAt: Instant,
    val endAt: Instant?,
    val allDay: Boolean,
    val reminderMinutes: List<Int>,
    val status: EventStatus,
    val sourceTranscript: String?,
    val aiConfidence: Double,
)

