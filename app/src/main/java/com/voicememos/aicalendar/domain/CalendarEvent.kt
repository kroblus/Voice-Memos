package com.voicememos.aicalendar.domain

import java.time.Instant

enum class NotificationStatus {
    PENDING,
    SCHEDULED,
    FAILED,
    NOT_REQUIRED,
}

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCED,
    FAILED,
}

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
    val itemType: ScheduleItemType = ScheduleItemType.EVENT,
    val triggerAt: Instant? = null,
    val dueAt: Instant? = null,
    val location: String? = null,
    val recurrenceRule: String? = null,
    val priority: ScheduleItemPriority? = null,
    val notificationStatus: NotificationStatus = NotificationStatus.PENDING,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val externalCalendarEventId: String? = null,
    val creationKey: String? = null,
)
