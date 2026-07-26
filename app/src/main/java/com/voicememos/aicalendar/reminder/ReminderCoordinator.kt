package com.voicememos.aicalendar.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.voicememos.aicalendar.calendar.CalendarMirrorGateway
import com.voicememos.aicalendar.calendar.CalendarMirrorResult
import com.voicememos.aicalendar.data.CalendarRepository
import com.voicememos.aicalendar.data.customAppUri
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.NotificationStatus
import com.voicememos.aicalendar.domain.SyncStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReconcileReason {
    STARTUP,
    RESUME,
    ITEM_CHANGED,
    IMPORT,
    PERMISSION_CHANGED,
    BOOT,
    TIME_CHANGED,
    TIME_ZONE_CHANGED,
    PACKAGE_UPDATED,
    MANUAL,
}

data class ReminderReconcileResult(
    val notificationStatus: NotificationStatus,
    val syncStatus: SyncStatus,
    val warning: String? = null,
)

class ReminderCoordinator(
    context: Context,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: com.voicememos.aicalendar.data.SettingsRepository,
    private val calendarMirrorGateway: CalendarMirrorGateway,
    private val localReminderGateway: ReminderAlarmGateway,
) {
    private val appContext = context.applicationContext
    private val reconciliationMutex = Mutex()

    suspend fun reconcileItem(event: CalendarEvent): ReminderReconcileResult =
        reconciliationMutex.withLock {
            reconcileItemLocked(event)
        }

    suspend fun reconcileAll(reason: ReconcileReason): List<ReminderReconcileResult> =
        reconciliationMutex.withLock {
            retryTombstonesLocked()
            calendarRepository.getAll().map { event ->
                reconcileItemLocked(event)
            }
        }

    suspend fun removeItem(event: CalendarEvent): Boolean =
        reconciliationMutex.withLock {
            localReminderGateway.cancel(event.id, event.reminderMinutes)
            val calendarEnabled = settingsRepository.isSystemCalendarEnabled()
            if (event.externalCalendarEventId != null || calendarEnabled) {
                calendarRepository.addMirrorTombstone(event)
            }
            calendarRepository.delete(event.id)
            val removed = if (event.externalCalendarEventId != null || calendarEnabled) {
                calendarMirrorGateway.delete(
                    externalEventId = event.externalCalendarEventId,
                    customAppUri = event.customAppUri(),
                )
            } else {
                true
            }
            if (removed) calendarRepository.deleteMirrorTombstone(event.id)
            removed
        }

    suspend fun enableSystemCalendar(): Boolean {
        if (!calendarMirrorGateway.hasCalendarPermissions()) return false
        settingsRepository.setSystemCalendarEnabled(true)
        reconcileAll(ReconcileReason.PERMISSION_CHANGED)
        return true
    }

    suspend fun disableAndDeleteSystemCalendar(): Boolean {
        if (!calendarMirrorGateway.hasCalendarPermissions()) return false
        return reconciliationMutex.withLock {
            if (!calendarMirrorGateway.deleteDedicatedCalendar()) return@withLock false
            calendarRepository.resetAllMirrorLinks()
            calendarRepository.clearMirrorTombstones()
            settingsRepository.setSystemCalendarEnabled(false)
            calendarRepository.getAll().forEach { event ->
                reconcileItemLocked(event, calendarEnabledOverride = false)
            }
            true
        }
    }

    suspend fun deleteAllItems(events: List<CalendarEvent>): Boolean {
        var complete = true
        events.forEach { event ->
            if (!removeItem(event)) complete = false
        }
        return complete
    }

    private suspend fun reconcileItemLocked(
        event: CalendarEvent,
        calendarEnabledOverride: Boolean? = null,
    ): ReminderReconcileResult {
        val calendarEnabled =
            calendarEnabledOverride ?: settingsRepository.isSystemCalendarEnabled()
        val anchor = event.reminderAnchor()
        val shouldNotify =
            event.status == EventStatus.SCHEDULED && anchor?.isAfter(Instant.now()) == true
        if (!shouldNotify) {
            localReminderGateway.cancel(event.id, event.reminderMinutes)
            val removed = if (event.externalCalendarEventId != null) {
                calendarMirrorGateway.delete(
                    externalEventId = event.externalCalendarEventId,
                    customAppUri = event.customAppUri(),
                )
            } else {
                true
            }
            if (removed) {
                calendarRepository.updateMirrorState(
                    id = event.id,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    notificationStatus = NotificationStatus.NOT_REQUIRED,
                    externalCalendarEventId = null,
                    timeZoneId = event.timeZoneId ?: ZoneId.systemDefault().id,
                )
            }
            return ReminderReconcileResult(
                notificationStatus = NotificationStatus.NOT_REQUIRED,
                syncStatus = if (removed) SyncStatus.LOCAL_ONLY else SyncStatus.FAILED,
                warning = if (removed) null else "系统日历中的旧副本暂时无法删除",
            )
        }

        if (!calendarEnabled) {
            if (event.externalCalendarEventId != null &&
                calendarMirrorGateway.hasCalendarPermissions()
            ) {
                calendarMirrorGateway.delete(
                    externalEventId = event.externalCalendarEventId,
                    customAppUri = event.customAppUri(),
                )
            }
            return scheduleLocalLocked(event, SyncStatus.LOCAL_ONLY, null)
        }

        val result = calendarMirrorGateway.upsert(event)
        return when (result) {
            is CalendarMirrorResult.Success -> {
                localReminderGateway.cancel(event.id, event.reminderMinutes)
                calendarRepository.updateMirrorState(
                    id = event.id,
                    syncStatus = SyncStatus.SYNCED,
                    notificationStatus = NotificationStatus.SYSTEM_SCHEDULED,
                    externalCalendarEventId = result.externalEventId,
                    timeZoneId = result.timeZoneId,
                )
                ReminderReconcileResult(
                    notificationStatus = NotificationStatus.SYSTEM_SCHEDULED,
                    syncStatus = SyncStatus.SYNCED,
                )
            }
            CalendarMirrorResult.NotRequired -> {
                localReminderGateway.cancel(event.id, event.reminderMinutes)
                calendarRepository.updateMirrorState(
                    id = event.id,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    notificationStatus = NotificationStatus.NOT_REQUIRED,
                    externalCalendarEventId = null,
                    timeZoneId = event.timeZoneId ?: ZoneId.systemDefault().id,
                )
                ReminderReconcileResult(
                    notificationStatus = NotificationStatus.NOT_REQUIRED,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                )
            }
            is CalendarMirrorResult.Failure -> {
                if (event.externalCalendarEventId != null) {
                    localReminderGateway.cancel(event.id, event.reminderMinutes)
                    calendarRepository.updateMirrorState(
                        id = event.id,
                        syncStatus = SyncStatus.FAILED,
                        notificationStatus = NotificationStatus.SYSTEM_SCHEDULED,
                        externalCalendarEventId = event.externalCalendarEventId,
                        timeZoneId = event.timeZoneId ?: ZoneId.systemDefault().id,
                    )
                    ReminderReconcileResult(
                        notificationStatus = NotificationStatus.SYSTEM_SCHEDULED,
                        syncStatus = SyncStatus.FAILED,
                        warning = "系统日历副本暂时无法更新，旧提醒可能已过期",
                    )
                } else {
                    scheduleLocalLocked(
                        event = event,
                        syncStatus = SyncStatus.FAILED,
                        leadingWarning = "系统日历不可用，已改用本地备用提醒",
                    )
                }
            }
        }
    }

    private suspend fun scheduleLocalLocked(
        event: CalendarEvent,
        syncStatus: SyncStatus,
        leadingWarning: String?,
    ): ReminderReconcileResult {
        val results = localReminderGateway.scheduleWithResults(event)
        val notificationAllowed = notificationsAllowed()
        val notificationStatus = results.toNotificationStatus(notificationAllowed)
        calendarRepository.updateMirrorState(
            id = event.id,
            syncStatus = syncStatus,
            notificationStatus = notificationStatus,
            externalCalendarEventId = null,
            timeZoneId = event.timeZoneId ?: ZoneId.systemDefault().id,
        )
        val localWarning = when {
            !notificationAllowed -> "本地备用提醒需要开启声历通知权限"
            results.values.any { it == ScheduleResult.FAILED } -> "本地备用提醒安排失败"
            results.values.any { it == ScheduleResult.SKIPPED_PAST } -> "部分提醒时间已经过去"
            results.values.any { it == ScheduleResult.INEXACT } -> "未获准精确闹钟，备用提醒可能延迟"
            else -> null
        }
        val warning = listOfNotNull(leadingWarning, localWarning).joinToString("；")
            .ifBlank { null }
        return ReminderReconcileResult(
            notificationStatus = notificationStatus,
            syncStatus = syncStatus,
            warning = warning?.let { "$it；Flyme 划掉声历后备用提醒可能失效" },
        )
    }

    private suspend fun retryTombstonesLocked() {
        if (!calendarMirrorGateway.hasCalendarPermissions()) return
        calendarRepository.getMirrorTombstones().forEach { tombstone ->
            val removed = calendarMirrorGateway.delete(
                externalEventId = tombstone.externalCalendarEventId,
                customAppUri = tombstone.customAppUri,
            )
            if (removed) calendarRepository.deleteMirrorTombstone(tombstone.localEventId)
        }
    }

    private fun notificationsAllowed(): Boolean {
        val runtimeAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return runtimeAllowed &&
            appContext.getSystemService(NotificationManager::class.java)
                .areNotificationsEnabled()
    }
}
