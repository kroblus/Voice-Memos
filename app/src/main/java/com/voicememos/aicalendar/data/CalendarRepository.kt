package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.NotificationStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalendarRepository(
    private val dao: CalendarEventDao,
) {
    fun observeEvents(rangeStart: Instant, rangeEnd: Instant): Flow<List<CalendarEvent>> =
        dao.observeEvents(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
            .map { events -> events.map(CalendarEventEntity::toDomain) }

    fun observeAllActive(): Flow<List<CalendarEvent>> =
        dao.observeAllActive().map { events -> events.map(CalendarEventEntity::toDomain) }

    suspend fun futureScheduled(from: Instant): List<CalendarEvent> =
        dao.futureScheduled(from.toEpochMilli()).map(CalendarEventEntity::toDomain)

    suspend fun createFromDraft(
        draft: DraftCalendarEvent,
        creationKey: String? = null,
    ): CalendarEvent {
        val normalizedCreationKey = creationKey?.trim()?.takeIf(String::isNotEmpty)
        val entity = draft.toEntity(creationKey = normalizedCreationKey)
        if (normalizedCreationKey == null) {
            dao.upsert(entity)
            return entity.toDomain()
        }

        if (dao.insertIgnore(entity) != INSERT_IGNORED) {
            return entity.toDomain()
        }
        return requireNotNull(dao.findByCreationKey(normalizedCreationKey)) {
            "Creation key conflict was reported, but no persisted event could be found."
        }.toDomain()
    }

    suspend fun importEvents(events: List<CalendarEvent>): Int {
        if (events.isEmpty()) return 0
        dao.upsertAll(events.map(CalendarEvent::toEntity))
        return events.size
    }

    suspend fun updateStatus(id: String, status: EventStatus) {
        dao.updateStatus(
            id = id,
            status = status.name,
            updatedAt = Instant.now().toEpochMilli(),
        )
    }

    suspend fun updateNotificationStatus(
        id: String,
        status: NotificationStatus,
        updatedAt: Instant = Instant.now(),
    ) {
        dao.updateNotificationStatus(
            id = id,
            notificationStatus = status.name,
            updatedAt = updatedAt.toEpochMilli(),
        )
    }

    suspend fun updateDetails(
        original: CalendarEvent,
        title: String,
        notes: String?,
        startAt: Instant,
        endAt: Instant?,
        allDay: Boolean,
        reminderMinutes: List<Int>,
    ): CalendarEvent {
        val updatedTriggerAt = when (original.itemType) {
            com.voicememos.aicalendar.domain.ScheduleItemType.REMINDER -> startAt
            else -> original.triggerAt
        }
        val updatedDueAt = when {
            original.itemType == com.voicememos.aicalendar.domain.ScheduleItemType.TODO &&
                original.dueAt != null -> startAt
            else -> original.dueAt
        }
        val updated = original.copy(
            title = title.trim(),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            startAt = startAt,
            endAt = endAt,
            triggerAt = updatedTriggerAt,
            dueAt = updatedDueAt,
            allDay = allDay,
            reminderMinutes = reminderMinutes.toReminderCsv().toReminderMinutes(),
        )
        dao.updateDetails(
            id = original.id,
            title = updated.title,
            notes = updated.notes,
            startAt = updated.startAt.toEpochMilli(),
            endAt = updated.endAt?.toEpochMilli(),
            triggerAt = updated.triggerAt?.toEpochMilli(),
            dueAt = updated.dueAt?.toEpochMilli(),
            allDay = updated.allDay,
            reminderMinutes = updated.reminderMinutes.toReminderCsv(),
            updatedAt = Instant.now().toEpochMilli(),
        )
        return updated
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteCompleted() {
        dao.deleteByStatus(EventStatus.DONE.name)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}
