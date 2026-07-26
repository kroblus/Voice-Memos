package com.voicememos.aicalendar.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import com.voicememos.aicalendar.data.customAppUri
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarIdentity(
    val accountName: String,
    val internalName: String,
    val displayName: String,
) {
    companion object {
        val Production = CalendarIdentity(
            accountName = "com.voicememos.aicalendar",
            internalName = "shengli_local",
            displayName = "声历",
        )
    }
}

data class CalendarMirrorRecord(
    val eventId: String,
    val customAppUri: String,
)

data class CalendarMirrorPayload(
    val localEventId: String,
    val title: String,
    val notes: String?,
    val location: String?,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long,
    val allDay: Boolean,
    val timeZoneId: String,
    val availability: Int,
    val reminderMinutes: List<Int>,
    val customAppUri: String,
)

sealed interface CalendarMirrorResult {
    data class Success(
        val externalEventId: String,
        val timeZoneId: String,
    ) : CalendarMirrorResult

    data object NotRequired : CalendarMirrorResult

    data class Failure(
        val reason: String,
        val permissionMissing: Boolean = false,
    ) : CalendarMirrorResult
}

interface CalendarMirrorGateway {
    fun hasCalendarPermissions(): Boolean

    suspend fun ensureCalendar(): Long

    suspend fun read(event: CalendarEvent): CalendarMirrorRecord?

    suspend fun upsert(event: CalendarEvent, now: Instant = Instant.now()): CalendarMirrorResult

    suspend fun delete(externalEventId: String?, customAppUri: String): Boolean

    suspend fun deleteDedicatedCalendar(): Boolean
}

fun CalendarEvent.toCalendarMirrorPayload(
    now: Instant = Instant.now(),
    fallbackZoneId: ZoneId = ZoneId.systemDefault(),
): CalendarMirrorPayload? {
    if (status != EventStatus.SCHEDULED) return null
    val anchor = when (itemType) {
        ScheduleItemType.EVENT -> startAt
        ScheduleItemType.REMINDER -> triggerAt
        ScheduleItemType.TODO -> dueAt
    } ?: return null
    if (!anchor.isAfter(now)) return null

    val zone = timeZoneId
        ?.let { value -> runCatching { ZoneId.of(value) }.getOrNull() }
        ?: fallbackZoneId
    val startMillis: Long
    val endMillis: Long
    val providerTimeZone: String
    if (allDay && itemType != ScheduleItemType.REMINDER) {
        val startDate = anchor.atZone(zone).toLocalDate()
        val candidateEndDate = endAt?.atZone(zone)?.toLocalDate()
        val endDate = candidateEndDate
            ?.takeIf { it.isAfter(startDate) }
            ?: startDate.plusDays(1)
        startMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        endMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        providerTimeZone = ZoneOffset.UTC.id
    } else {
        startMillis = anchor.toEpochMilli()
        val requestedEnd = when (itemType) {
            ScheduleItemType.EVENT -> endAt ?: anchor.plus(DEFAULT_EVENT_DURATION)
            ScheduleItemType.REMINDER,
            ScheduleItemType.TODO,
            -> anchor.plus(MIRROR_MARKER_DURATION)
        }
        endMillis = requestedEnd.takeIf { it.isAfter(anchor) }
            ?.toEpochMilli()
            ?: anchor.plus(MIRROR_MARKER_DURATION).toEpochMilli()
        providerTimeZone = zone.id
    }
    val reminders = reminderMinutes
        .asSequence()
        .plus(0)
        .filter { it >= 0 }
        .distinct()
        .sorted()
        .filter { minutes -> anchor.minusSeconds(minutes * 60L).isAfter(now) }
        .take(MAX_REMINDERS)
        .toList()
    if (reminders.isEmpty()) return null

    return CalendarMirrorPayload(
        localEventId = id,
        title = title,
        notes = notes,
        location = location,
        startAtEpochMillis = startMillis,
        endAtEpochMillis = endMillis,
        allDay = allDay && itemType != ScheduleItemType.REMINDER,
        timeZoneId = providerTimeZone,
        availability = if (itemType == ScheduleItemType.EVENT) {
            Events.AVAILABILITY_BUSY
        } else {
            Events.AVAILABILITY_FREE
        },
        reminderMinutes = reminders,
        customAppUri = customAppUri(),
    )
}

class AndroidCalendarMirrorGateway(
    context: Context,
    private val identity: CalendarIdentity = CalendarIdentity.Production,
) : CalendarMirrorGateway {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override fun hasCalendarPermissions(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            appContext.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun ensureCalendar(): Long = withContext(Dispatchers.IO) {
        requirePermissions()
        findCalendarId() ?: insertCalendar()
    }

    override suspend fun read(event: CalendarEvent): CalendarMirrorRecord? =
        withContext(Dispatchers.IO) {
            if (!hasCalendarPermissions()) return@withContext null
            val calendarId = findCalendarId() ?: return@withContext null
            findEvent(calendarId, event.externalCalendarEventId, event.customAppUri())
        }

    override suspend fun upsert(
        event: CalendarEvent,
        now: Instant,
    ): CalendarMirrorResult = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions()) {
            return@withContext CalendarMirrorResult.Failure(
                reason = "需要开启系统日历读写权限",
                permissionMissing = true,
            )
        }
        val payload = event.toCalendarMirrorPayload(now)
            ?: return@withContext CalendarMirrorResult.NotRequired
        runCatching {
            val calendarId = ensureCalendar()
            val existing = findEvent(
                calendarId = calendarId,
                externalEventId = event.externalCalendarEventId,
                customAppUri = payload.customAppUri,
            )
            val values = payload.toContentValues(calendarId)
            val operations = arrayListOf<ContentProviderOperation>()
            if (existing == null) {
                operations += ContentProviderOperation.newInsert(Events.CONTENT_URI)
                    .withValues(values)
                    .build()
                payload.reminderMinutes.forEach { minutes ->
                    operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                        .withValueBackReference(Reminders.EVENT_ID, 0)
                        .withValue(Reminders.MINUTES, minutes)
                        .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                        .build()
                }
            } else {
                val eventUri = ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    existing.eventId.toLong(),
                )
                operations += ContentProviderOperation.newUpdate(eventUri)
                    .withValues(values)
                    .build()
                operations += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                    .withSelection(
                        "${Reminders.EVENT_ID} = ?",
                        arrayOf(existing.eventId),
                    )
                    .build()
                payload.reminderMinutes.forEach { minutes ->
                    operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                        .withValue(Reminders.EVENT_ID, existing.eventId.toLong())
                        .withValue(Reminders.MINUTES, minutes)
                        .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                        .build()
                }
            }
            val results = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
            val externalId = existing?.eventId
                ?: results.firstOrNull()?.uri?.let(ContentUris::parseId)?.toString()
                ?: error("系统日历未返回事件 ID")
            CalendarMirrorResult.Success(
                externalEventId = externalId,
                timeZoneId = event.timeZoneId ?: ZoneId.systemDefault().id,
            )
        }.getOrElse { error ->
            CalendarMirrorResult.Failure(
                reason = error.message ?: "系统日历写入失败",
                permissionMissing = error is SecurityException,
            )
        }
    }

    override suspend fun delete(
        externalEventId: String?,
        customAppUri: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions()) return@withContext false
        runCatching {
            val calendarId = findCalendarId() ?: return@runCatching true
            val event = findEvent(calendarId, externalEventId, customAppUri)
                ?: return@runCatching true
            resolver.delete(
                ContentUris.withAppendedId(Events.CONTENT_URI, event.eventId.toLong()),
                null,
                null,
            )
            true
        }.getOrDefault(false)
    }

    override suspend fun deleteDedicatedCalendar(): Boolean = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions()) return@withContext false
        runCatching {
            val calendarId = findCalendarId() ?: return@runCatching true
            resolver.delete(
                asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)),
                null,
                null,
            )
            true
        }.getOrDefault(false)
    }

    private fun findCalendarId(): Long? =
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.NAME} = ?",
            arrayOf(
                identity.accountName,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
                identity.internalName,
            ),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    private fun insertCalendar(): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, identity.accountName)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, identity.internalName)
            put(Calendars.CALENDAR_DISPLAY_NAME, identity.displayName)
            put(Calendars.CALENDAR_COLOR, Color.rgb(30, 104, 92))
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, identity.accountName)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT.toString())
            put(Calendars.MAX_REMINDERS, MAX_REMINDERS)
        }
        val uri = resolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)
            ?: error("无法创建声历本地日历")
        return ContentUris.parseId(uri)
    }

    private fun findEvent(
        calendarId: Long,
        externalEventId: String?,
        customAppUri: String,
    ): CalendarMirrorRecord? {
        val requestedId = externalEventId?.toLongOrNull()
        val selection: String
        val arguments: Array<String>
        if (requestedId != null) {
            selection =
                "${Events.CALENDAR_ID} = ? AND (${Events._ID} = ? OR ${Events.CUSTOM_APP_URI} = ?)"
            arguments = arrayOf(calendarId.toString(), requestedId.toString(), customAppUri)
        } else {
            selection = "${Events.CALENDAR_ID} = ? AND ${Events.CUSTOM_APP_URI} = ?"
            arguments = arrayOf(calendarId.toString(), customAppUri)
        }
        return resolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.CUSTOM_APP_URI),
            selection,
            arguments,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            CalendarMirrorRecord(
                eventId = cursor.getLong(0).toString(),
                customAppUri = cursor.getString(1).orEmpty(),
            )
        }
    }

    private fun CalendarMirrorPayload.toContentValues(calendarId: Long): ContentValues =
        ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title)
            put(Events.DESCRIPTION, notes)
            put(Events.EVENT_LOCATION, location)
            put(Events.DTSTART, startAtEpochMillis)
            put(Events.DTEND, endAtEpochMillis)
            put(Events.ALL_DAY, if (allDay) 1 else 0)
            put(Events.EVENT_TIMEZONE, timeZoneId)
            put(Events.EVENT_END_TIMEZONE, timeZoneId)
            put(Events.STATUS, Events.STATUS_CONFIRMED)
            put(Events.AVAILABILITY, availability)
            put(Events.HAS_ALARM, 1)
            put(Events.CUSTOM_APP_PACKAGE, appContext.packageName)
            put(Events.CUSTOM_APP_URI, customAppUri)
        }

    private fun asSyncAdapter(uri: Uri): Uri =
        uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, identity.accountName)
            .appendQueryParameter(
                Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()

    private fun requirePermissions() {
        if (!hasCalendarPermissions()) throw SecurityException("Calendar permission missing")
    }
}

private const val MAX_REMINDERS = 5
private val MIRROR_MARKER_DURATION: Duration = Duration.ofMinutes(1)
private val DEFAULT_EVENT_DURATION: Duration = Duration.ofMinutes(30)
