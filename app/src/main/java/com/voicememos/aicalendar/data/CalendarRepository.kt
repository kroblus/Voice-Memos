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

    suspend fun updateStatus(id: String, status: EventStatus) {
        dao.updateStatus(
            id = id,
            status = status.name,
            updatedAt = Instant.now().toEpochMilli(),
        )
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}

