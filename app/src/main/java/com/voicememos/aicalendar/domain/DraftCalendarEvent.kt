package com.voicememos.aicalendar.domain

import java.time.Instant
import java.util.UUID

data class DraftCalendarEvent(
    val draftId: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String?,
    val startAt: Instant?,
    val endAt: Instant?,
    val allDay: Boolean,
    val reminderMinutes: List<Int>,
    val sourceTranscript: String,
    val aiConfidence: Double,
    val missingFields: List<String>,
) {
    val canCreate: Boolean
        get() = title.isNotBlank() && startAt != null && missingFields.isEmpty()
}

