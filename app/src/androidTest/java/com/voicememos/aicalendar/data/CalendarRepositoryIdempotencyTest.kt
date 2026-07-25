package com.voicememos.aicalendar.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarRepositoryIdempotencyTest {
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
    fun sameCreationKeyReturnsOriginalPersistedEvent() = runBlocking {
        val repository = CalendarRepository(database.calendarEventDao())
        val first = repository.createFromDraft(
            draft = draft(title = "第一次"),
            creationKey = "session-1:item-1",
        )
        val duplicate = repository.createFromDraft(
            draft = draft(title = "重复提交"),
            creationKey = "session-1:item-1",
        )

        assertEquals(first.id, duplicate.id)
        assertEquals("第一次", duplicate.title)
        assertEquals(
            first.id,
            database.calendarEventDao()
                .findByCreationKey("session-1:item-1")
                ?.id,
        )
    }

    @Test
    fun concurrentCreationWithSameKeyPersistsOneEvent() = runBlocking {
        val repository = CalendarRepository(database.calendarEventDao())
        val results = (1..8).map { attempt ->
            async(Dispatchers.Default) {
                repository.createFromDraft(
                    draft = draft(title = "并发提交 $attempt"),
                    creationKey = "session-concurrent:item-1",
                )
            }
        }.awaitAll()

        assertEquals(1, results.map { it.id }.distinct().size)
        assertEquals(
            results.first().id,
            database.calendarEventDao()
                .findByCreationKey("session-concurrent:item-1")
                ?.id,
        )
    }

    private fun draft(title: String): DraftCalendarEvent =
        DraftCalendarEvent(
            title = title,
            notes = null,
            startAt = Instant.parse("2026-07-26T01:00:00Z"),
            endAt = null,
            allDay = false,
            reminderMinutes = listOf(0),
            sourceTranscript = title,
            aiConfidence = 1.0,
            missingFields = emptyList(),
        )
}
