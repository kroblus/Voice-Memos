package com.voicememos.aicalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long?,
    val allDay: Boolean,
    val reminderMinutesCsv: String,
    val status: String,
    val sourceTranscript: String?,
    val aiConfidence: Double,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

