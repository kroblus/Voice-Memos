package com.voicememos.aicalendar.domain

import java.time.Instant
import java.util.UUID

enum class RepeatFrequency(val label: String) {
    DAILY("每天"),
    WEEKLY("每周"),
}

data class RepeatRule(
    val frequency: RepeatFrequency,
    val count: Int,
) {
    val normalizedCount: Int
        get() = count.coerceIn(2, 60)

    fun summary(): String = "${frequency.label}，共 $normalizedCount 次"
}

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
    val repeatRule: RepeatRule? = null,
) {
    val canCreate: Boolean
        get() = title.isNotBlank() && startAt != null && missingFields.isEmpty()
}
