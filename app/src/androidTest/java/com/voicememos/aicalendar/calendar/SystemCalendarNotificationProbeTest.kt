package com.voicememos.aicalendar.calendar

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.voicememos.aicalendar.ShengliApplication
import com.voicememos.aicalendar.data.AppDatabase
import com.voicememos.aicalendar.data.CalendarRepository
import com.voicememos.aicalendar.data.SettingsRepository
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.NotificationStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemCalendarNotificationProbeTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @Test
    fun createTwoMinuteProductionCalendarProbe() = runBlocking {
        assumeTrue(runProbe())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val database = AppDatabase.get(context)
        val repository = CalendarRepository(
            dao = database.calendarEventDao(),
            tombstoneDao = database.calendarMirrorTombstoneDao(),
        )
        val now = Instant.now()
        val triggerAt = now.plusSeconds(PROBE_DELAY_SECONDS)
        val event = repository.createFromDraft(
            DraftCalendarEvent(
                title = PROBE_TITLE,
                notes = "用于验证 Flyme 强制停止后仍由系统日历提醒",
                startAt = triggerAt,
                endAt = triggerAt.plusSeconds(60),
                allDay = false,
                reminderMinutes = listOf(0),
                sourceTranscript = "device notification probe",
                aiConfidence = 1.0,
                missingFields = emptyList(),
                timeZoneId = ZoneId.systemDefault().id,
            ),
            creationKey = "system-calendar-notification-probe",
        )
        val application = context.applicationContext as ShengliApplication

        assertEquals(true, application.container.reminderCoordinator.enableSystemCalendar())
        val persisted = repository.getById(event.id)
        assertNotNull(persisted)
        assertEquals(NotificationStatus.SYSTEM_SCHEDULED, persisted?.notificationStatus)
    }

    @Test
    fun cleanupProductionCalendarProbe() = runBlocking {
        assumeTrue(runProbe())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = AndroidCalendarMirrorGateway(context)
        assertEquals(true, gateway.deleteDedicatedCalendar())
        val database = AppDatabase.get(context)
        CalendarRepository(
            dao = database.calendarEventDao(),
            tombstoneDao = database.calendarMirrorTombstoneDao(),
        ).resetAllMirrorLinks()
        SettingsRepository(database.appSettingDao()).setSystemCalendarEnabled(false)
    }

    private fun runProbe(): Boolean =
        InstrumentationRegistry.getArguments().getString("runNotificationProbe") == "true"

    private companion object {
        const val PROBE_TITLE = "声历系统托管实机测试"
        const val PROBE_DELAY_SECONDS = 150L
    }
}
