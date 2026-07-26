package com.voicememos.aicalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CalendarMirrorTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tombstone: CalendarMirrorTombstoneEntity)

    @Query("SELECT * FROM calendar_mirror_tombstones ORDER BY createdAtEpochMillis ASC")
    suspend fun getAll(): List<CalendarMirrorTombstoneEntity>

    @Query("DELETE FROM calendar_mirror_tombstones WHERE localEventId = :localEventId")
    suspend fun delete(localEventId: String)

    @Query("DELETE FROM calendar_mirror_tombstones")
    suspend fun deleteAll()
}
