package com.voicememos.aicalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<CalendarEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: CalendarEventEntity): Long

    @Query("SELECT * FROM calendar_events WHERE creationKey = :creationKey LIMIT 1")
    suspend fun findByCreationKey(creationKey: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events ORDER BY createdAtEpochMillis ASC")
    suspend fun getAll(): List<CalendarEventEntity>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE status != 'ARCHIVED'
        AND startAtEpochMillis >= :rangeStartEpochMillis
        AND startAtEpochMillis < :rangeEndEpochMillis
        ORDER BY startAtEpochMillis ASC
        """,
    )
    fun observeEvents(
        rangeStartEpochMillis: Long,
        rangeEndEpochMillis: Long,
    ): Flow<List<CalendarEventEntity>>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE status != 'ARCHIVED'
        ORDER BY startAtEpochMillis ASC
        """,
    )
    fun observeAllActive(): Flow<List<CalendarEventEntity>>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE status = 'SCHEDULED'
        AND CASE itemType
                WHEN 'REMINDER' THEN triggerAtEpochMillis
                WHEN 'TODO' THEN dueAtEpochMillis
                ELSE startAtEpochMillis
            END >= :fromEpochMillis
        ORDER BY CASE itemType
                WHEN 'REMINDER' THEN triggerAtEpochMillis
                WHEN 'TODO' THEN dueAtEpochMillis
                ELSE startAtEpochMillis
            END ASC
        """,
    )
    suspend fun futureScheduled(fromEpochMillis: Long): List<CalendarEventEntity>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE status = 'SCHEDULED'
        AND CASE itemType
                WHEN 'REMINDER' THEN triggerAtEpochMillis
                WHEN 'TODO' THEN dueAtEpochMillis
                ELSE startAtEpochMillis
            END >= :nowEpochMillis
        ORDER BY CASE itemType
                WHEN 'REMINDER' THEN triggerAtEpochMillis
                WHEN 'TODO' THEN dueAtEpochMillis
                ELSE startAtEpochMillis
            END ASC
        LIMIT 1
        """,
    )
    suspend fun nextScheduled(nowEpochMillis: Long): CalendarEventEntity?

    @Query(
        """
        UPDATE calendar_events
        SET status = :status,
            syncStatus = 'PENDING',
            updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query(
        """
        UPDATE calendar_events
        SET notificationStatus = :notificationStatus,
            updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateNotificationStatus(
        id: String,
        notificationStatus: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE calendar_events
        SET syncStatus = :syncStatus,
            notificationStatus = :notificationStatus,
            externalCalendarEventId = :externalCalendarEventId,
            timeZoneId = :timeZoneId,
            updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateMirrorState(
        id: String,
        syncStatus: String,
        notificationStatus: String,
        externalCalendarEventId: String?,
        timeZoneId: String?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE calendar_events
        SET title = :title,
            notes = :notes,
            startAtEpochMillis = :startAt,
            endAtEpochMillis = :endAt,
            triggerAtEpochMillis = :triggerAt,
            dueAtEpochMillis = :dueAt,
            allDay = :allDay,
            reminderMinutesCsv = :reminderMinutes,
            timeZoneId = :timeZoneId,
            notificationStatus = 'PENDING',
            syncStatus = 'PENDING',
            updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateDetails(
        id: String,
        title: String,
        notes: String?,
        startAt: Long,
        endAt: Long?,
        triggerAt: Long?,
        dueAt: Long?,
        allDay: Boolean,
        reminderMinutes: String,
        timeZoneId: String?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE calendar_events
        SET syncStatus = 'LOCAL_ONLY',
            notificationStatus = 'PENDING',
            externalCalendarEventId = NULL,
            updatedAtEpochMillis = :updatedAt
        """,
    )
    suspend fun resetAllMirrorLinks(updatedAt: Long)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM calendar_events WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM calendar_events WHERE title = :title")
    suspend fun countByTitle(title: String): Int
}
