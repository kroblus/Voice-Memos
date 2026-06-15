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

    @Query("UPDATE calendar_events SET status = :status, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: String)
}

