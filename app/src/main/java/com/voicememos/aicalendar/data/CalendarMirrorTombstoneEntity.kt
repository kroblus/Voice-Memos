package com.voicememos.aicalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_mirror_tombstones")
data class CalendarMirrorTombstoneEntity(
    @PrimaryKey val localEventId: String,
    val itemType: String,
    val externalCalendarEventId: String?,
    val customAppUri: String,
    val createdAtEpochMillis: Long,
)
