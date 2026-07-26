package com.voicememos.aicalendar.calendar

import android.Manifest
import android.content.ContentUris
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarMirrorGatewayTest {
    @get:Rule
    val calendarPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    @Test
    fun isolatedLocalCalendarSupportsIdempotentCreateUpdateAndDelete() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val gateway = AndroidCalendarMirrorGateway(
            context = context,
            identity = CalendarIdentity(
                accountName = "com.voicememos.aicalendar.test.$suffix",
                internalName = "shengli_test_$suffix",
                displayName = "声历测试",
            ),
        )
        val event = CalendarEvent(
            id = suffix,
            title = "Provider 创建测试",
            notes = "只写入测试日历",
            startAt = Instant.now().plusSeconds(3600),
            endAt = Instant.now().plusSeconds(5400),
            allDay = false,
            reminderMinutes = listOf(0, 10),
            status = EventStatus.SCHEDULED,
            sourceTranscript = "不写入 Provider",
            aiConfidence = 1.0,
            timeZoneId = "Asia/Shanghai",
        )

        try {
            val created = gateway.upsert(event)
            assertTrue(created is CalendarMirrorResult.Success)
            val createdId = (created as CalendarMirrorResult.Success).externalEventId
            assertNotNull(gateway.read(event.copy(externalCalendarEventId = createdId)))
            assertEquals(2, reminderCount(context, createdId))

            val updated = gateway.upsert(
                event.copy(
                    title = "Provider 更新测试",
                    externalCalendarEventId = createdId,
                ),
            )
            assertTrue(updated is CalendarMirrorResult.Success)
            assertEquals(createdId, (updated as CalendarMirrorResult.Success).externalEventId)
            assertEquals("Provider 更新测试", eventTitle(context, createdId))

            assertTrue(gateway.delete(createdId, event.customUri()))
            assertNull(gateway.read(event.copy(externalCalendarEventId = createdId)))
        } finally {
            assertTrue(gateway.deleteDedicatedCalendar())
        }
    }

    private fun reminderCount(
        context: android.content.Context,
        eventId: String,
    ): Int =
        context.contentResolver.query(
            Reminders.CONTENT_URI,
            arrayOf(Reminders._ID),
            "${Reminders.EVENT_ID} = ?",
            arrayOf(eventId),
            null,
        )?.use { it.count } ?: 0

    private fun eventTitle(
        context: android.content.Context,
        eventId: String,
    ): String? =
        context.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId.toLong()),
            arrayOf(Events.TITLE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun CalendarEvent.customUri(): String =
        "shengli://item/event/$id"
}
