package com.voicememos.aicalendar.reminder

import android.Manifest
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.voicememos.aicalendar.calendar.CalendarMirrorGateway
import com.voicememos.aicalendar.calendar.CalendarMirrorRecord
import com.voicememos.aicalendar.calendar.CalendarMirrorResult
import com.voicememos.aicalendar.data.AppDatabase
import com.voicememos.aicalendar.data.CalendarRepository
import com.voicememos.aicalendar.data.SettingsRepository
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.NotificationStatus
import com.voicememos.aicalendar.domain.SyncStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderCoordinatorTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var database: AppDatabase
    private lateinit var repository: CalendarRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var mirrorGateway: FakeCalendarMirrorGateway
    private lateinit var alarmGateway: FakeReminderAlarmGateway
    private lateinit var coordinator: ReminderCoordinator

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CalendarRepository(
            dao = database.calendarEventDao(),
            tombstoneDao = database.calendarMirrorTombstoneDao(),
        )
        settingsRepository = SettingsRepository(database.appSettingDao())
        mirrorGateway = FakeCalendarMirrorGateway()
        alarmGateway = FakeReminderAlarmGateway()
        coordinator = ReminderCoordinator(
            context = context,
            calendarRepository = repository,
            settingsRepository = settingsRepository,
            calendarMirrorGateway = mirrorGateway,
            localReminderGateway = alarmGateway,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun successfulMirrorCancelsLocalAlarmAndPersistsSystemOwnership() = runBlocking {
        val event = createFutureEvent()

        assertEquals(true, coordinator.enableSystemCalendar())

        val persisted = requireNotNull(repository.getById(event.id))
        assertEquals(NotificationStatus.SYSTEM_SCHEDULED, persisted.notificationStatus)
        assertEquals(SyncStatus.SYNCED, persisted.syncStatus)
        assertEquals("provider-42", persisted.externalCalendarEventId)
        assertEquals(1, alarmGateway.cancelCount)
        assertEquals(0, alarmGateway.scheduleCount)
    }

    @Test
    fun newMirrorFailureFallsBackToLocalAlarm() = runBlocking {
        val event = createFutureEvent()
        settingsRepository.setSystemCalendarEnabled(true)
        mirrorGateway.upsertResult = CalendarMirrorResult.Failure("provider unavailable")

        val result = coordinator.reconcileItem(event)

        val persisted = requireNotNull(repository.getById(event.id))
        assertEquals(NotificationStatus.SCHEDULED, persisted.notificationStatus)
        assertEquals(SyncStatus.FAILED, persisted.syncStatus)
        assertEquals(1, alarmGateway.scheduleCount)
        assertNotNull(result.warning)
    }

    @Test
    fun failedUpdateOfExistingMirrorDoesNotCreateDuplicateLocalAlarm() = runBlocking {
        val event = createFutureEvent().copy(externalCalendarEventId = "provider-old")
        repository.importEvents(listOf(event))
        repository.updateMirrorState(
            id = event.id,
            syncStatus = SyncStatus.SYNCED,
            notificationStatus = NotificationStatus.SYSTEM_SCHEDULED,
            externalCalendarEventId = "provider-old",
            timeZoneId = "Asia/Shanghai",
        )
        settingsRepository.setSystemCalendarEnabled(true)
        mirrorGateway.upsertResult = CalendarMirrorResult.Failure("provider unavailable")

        coordinator.reconcileItem(requireNotNull(repository.getById(event.id)))

        val persisted = requireNotNull(repository.getById(event.id))
        assertEquals(NotificationStatus.SYSTEM_SCHEDULED, persisted.notificationStatus)
        assertEquals(SyncStatus.FAILED, persisted.syncStatus)
        assertEquals("provider-old", persisted.externalCalendarEventId)
        assertEquals(0, alarmGateway.scheduleCount)
    }

    @Test
    fun failedExternalDeleteKeepsTombstoneAfterLocalRowIsRemoved() = runBlocking {
        val created = createFutureEvent()
        repository.updateMirrorState(
            id = created.id,
            syncStatus = SyncStatus.SYNCED,
            notificationStatus = NotificationStatus.SYSTEM_SCHEDULED,
            externalCalendarEventId = "provider-42",
            timeZoneId = "Asia/Shanghai",
        )
        mirrorGateway.deleteResult = false

        assertFalse(coordinator.removeItem(requireNotNull(repository.getById(created.id))))
        assertNull(repository.getById(created.id))
        assertEquals(1, repository.getMirrorTombstones().size)
    }

    private suspend fun createFutureEvent(): CalendarEvent =
        repository.createFromDraft(
            DraftCalendarEvent(
                title = "协调器测试",
                notes = null,
                startAt = Instant.now().plusSeconds(3600),
                endAt = Instant.now().plusSeconds(5400),
                allDay = false,
                reminderMinutes = listOf(0, 10),
                sourceTranscript = "测试",
                aiConfidence = 1.0,
                missingFields = emptyList(),
                timeZoneId = "Asia/Shanghai",
            ),
        )
}

private class FakeCalendarMirrorGateway : CalendarMirrorGateway {
    var upsertResult: CalendarMirrorResult =
        CalendarMirrorResult.Success("provider-42", "Asia/Shanghai")
    var deleteResult: Boolean = true

    override fun hasCalendarPermissions(): Boolean = true

    override suspend fun ensureCalendar(): Long = 1L

    override suspend fun read(event: CalendarEvent): CalendarMirrorRecord? = null

    override suspend fun upsert(event: CalendarEvent, now: Instant): CalendarMirrorResult =
        upsertResult

    override suspend fun delete(
        externalEventId: String?,
        customAppUri: String,
    ): Boolean = deleteResult

    override suspend fun deleteDedicatedCalendar(): Boolean = deleteResult
}

private class FakeReminderAlarmGateway : ReminderAlarmGateway {
    var scheduleCount: Int = 0
    var cancelCount: Int = 0

    override fun scheduleWithResults(event: CalendarEvent): Map<Int, ScheduleResult> {
        scheduleCount += 1
        return event.reminderMinutes.associateWith { ScheduleResult.EXACT }
    }

    override fun cancel(eventId: String, reminderMinutes: List<Int>) {
        cancelCount += 1
    }
}
