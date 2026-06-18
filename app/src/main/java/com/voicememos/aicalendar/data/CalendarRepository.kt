package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
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

    suspend fun createFromDraft(draft: DraftCalendarEvent): CalendarEvent {
        val entity = draft.toEntity()
        dao.upsert(entity)
        return entity.toDomain()
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

    suspend fun updateDetails(
        original: CalendarEvent,
        title: String,
        notes: String?,
        startAt: Instant,
        endAt: Instant?,
        allDay: Boolean,
        reminderMinutes: List<Int>,
    ): CalendarEvent {
        val updated = original.copy(
            title = title.trim(),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            startAt = startAt,
            endAt = endAt,
            allDay = allDay,
            reminderMinutes = reminderMinutes.toReminderCsv().toReminderMinutes(),
        )
        dao.updateDetails(
            id = original.id,
            title = updated.title,
            notes = updated.notes,
            startAt = updated.startAt.toEpochMilli(),
            endAt = updated.endAt?.toEpochMilli(),
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
}
