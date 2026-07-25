package com.voicememos.aicalendar.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voicememos.aicalendar.domain.NotificationStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarRepositoryNotificationStatusTest {
    private lateinit var database: AppDatabase

    @get:Rule
    val databaseRule = object : TestWatcher() {
        override fun starting(description: Description) {
            database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).build()
        }

        override fun finished(description: Description) {
            database.close()
        }
    }

    @Test
    fun updateNotificationStatusPersistsStatusAndUpdatedAtTogether() = runBlocking {
        val dao = database.calendarEventDao()
        dao.upsert(event())
        val updatedAt = Instant.parse("2026-07-25T09:30:00Z")

        CalendarRepository(dao).updateNotificationStatus(
            id = EVENT_ID,
            status = NotificationStatus.SCHEDULED,
            updatedAt = updatedAt,
        )

        val persisted = requireNotNull(dao.findByCreationKey(CREATION_KEY))
        assertEquals(NotificationStatus.SCHEDULED.name, persisted.notificationStatus)
        assertEquals(updatedAt.toEpochMilli(), persisted.updatedAtEpochMillis)
    }

    private fun event(): CalendarEventEntity =
        CalendarEventEntity(
            id = EVENT_ID,
            title = "项目会",
            notes = null,
            startAtEpochMillis = 1784966400000,
            endAtEpochMillis = null,
            allDay = false,
            reminderMinutesCsv = "0,10",
            status = "SCHEDULED",
            sourceTranscript = null,
            aiConfidence = 1.0,
            createdAtEpochMillis = 1784880000000,
            updatedAtEpochMillis = 1784880000000,
            creationKey = CREATION_KEY,
        )

    private companion object {
        const val EVENT_ID = "event-notification-status"
        const val CREATION_KEY = "session-notification:item-1"
    }
}
