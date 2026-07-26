package com.voicememos.aicalendar.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["creationKey"], unique = true),
    ],
)
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
    @ColumnInfo(defaultValue = "'EVENT'")
    val itemType: String = "EVENT",
    val triggerAtEpochMillis: Long? = null,
    val dueAtEpochMillis: Long? = null,
    val location: String? = null,
    val recurrenceRule: String? = null,
    val priority: String? = null,
    @ColumnInfo(defaultValue = "'PENDING'")
    val notificationStatus: String = "PENDING",
    @ColumnInfo(defaultValue = "'LOCAL_ONLY'")
    val syncStatus: String = "LOCAL_ONLY",
    val externalCalendarEventId: String? = null,
    val creationKey: String? = null,
    val timeZoneId: String? = null,
)
